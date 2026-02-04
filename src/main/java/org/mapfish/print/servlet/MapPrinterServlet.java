/*
 * Copyright (C) 2013  Camptocamp
 *
 * This file is part of MapFish Print
 *
 * MapFish Print is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MapFish Print is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MapFish Print.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mapfish.print.servlet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharStreams;
import com.google.common.io.Closer;
import com.lowagie.text.DocumentException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serial;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONWriter;
import org.mapfish.print.Constants;
import org.mapfish.print.MapPrinter;
import org.mapfish.print.output.OutputFormat;
import org.mapfish.print.utils.PJsonObject;
import org.pvalsecc.misc.FileUtilities;

/**
 * Main print servlet.
 */
public class MapPrinterServlet extends BaseMapServlet {
    public static final Logger SPEC_LOGGER = LogManager.getLogger(BaseMapServlet.class.getPackage().toString() + ".spec");
    protected static final String TEMP_FILE_PREFIX = "mapfish-print";
    protected static final String TEMP_FILE_METADATA_PREFIX = "mapfish-print-metadata";
    @Serial
    private static final long serialVersionUID = -4706371598927161642L;
    private static final String CONTEXT_TEMPDIR = "jakarta.servlet.context.tempdir";
    private static final String INFO_URL = "/info.json";
    private static final String PRINT_URL = "/print.pdf";
    private static final String CREATE_URL = "/create.json";
    private static final String TEMP_FILE_SUFFIX = ".printout";
    private static final int TEMP_FILE_PURGE_SECONDS = 10 * 60;
    /**
     * Map of temporary files.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * Map of temporary files.
     */
    private String app = null;
    private File tempDir = null;
    private String encoding = null;
    /**
     * Tells if a thread is alread purging the old temporary files or not.
     */
    private AtomicBoolean purging = new AtomicBoolean(false);

    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        //do the routing in function of the actual URL
        String additionalPath = httpServletRequest.getPathInfo().trim();
        if (additionalPath.isEmpty()) {
            // handle an odd case where path info returns an empty string
            additionalPath = httpServletRequest.getServletPath();
        }
        if (additionalPath.equals(PRINT_URL)) {
            createAndGetPDF(httpServletRequest, httpServletResponse);
        } else if (additionalPath.equals(INFO_URL)) {
            getInfo(httpServletRequest, httpServletResponse, getBaseUrl(httpServletRequest));
        } else if (additionalPath.startsWith("/") && additionalPath.endsWith(TEMP_FILE_SUFFIX)) {
            getFile(httpServletRequest, httpServletResponse, additionalPath.substring(1, additionalPath.length() - TEMP_FILE_SUFFIX.length()));
        } else {
            error(httpServletResponse, "Unknown method: " + additionalPath, 404);
        }
    }

    protected void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        final String additionalPath = httpServletRequest.getPathInfo();
        if (additionalPath.equals(PRINT_URL)) {
            createAndGetPDF(httpServletRequest, httpServletResponse);
        } else if (additionalPath.equals(CREATE_URL)) {
            createPDF(httpServletRequest, httpServletResponse, getBaseUrl(httpServletRequest));
        } else {
            error(httpServletResponse, "Unknown method: " + additionalPath, 404);
        }
    }

    public void init() throws ServletException {
        //get rid of the temporary files that were present before the servlet was started.
        File dir = getTempDir();
        File[] files = dir.listFiles();
        for (File file : files) {
            try {
                if (shouldFileBeDelete(file)) {
                    deleteFile(file);
                }
            } catch (IOException e) {
                LOGGER.debug("Unable to handle file :: ", e);
            }
        }
    }

    public boolean shouldFileBeDelete(File file) throws IOException {
        BasicFileAttributes attr = Files.readAttributes(Path.of(file.getPath()), BasicFileAttributes.class);
        FileTime creationTime = attr.creationTime();
        final long minTime = System.currentTimeMillis() - TEMP_FILE_PURGE_SECONDS * 1000L;
        return creationTime.toMillis() < minTime && (file.getName().startsWith(TEMP_FILE_PREFIX) || file.getName().startsWith(TEMP_FILE_METADATA_PREFIX)) && file.isFile();
    }

    public void destroy() {
        super.destroy();
    }

    /**
     * All in one method: create and returns the PDF to the client. Avoid to use
     * it, the accents in the spec are not all supported.
     */
    protected void createAndGetPDF(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        //get the spec from the query
        TempFileMetadata tempFileMetadata = null;
        String spec = null;
        try {
            httpServletRequest.setCharacterEncoding("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        if ("POST".equalsIgnoreCase(httpServletRequest.getMethod())) {
            try {
                spec = getSpecFromPostBody(httpServletRequest);
            } catch (IOException e) {
                error(httpServletResponse, "Missing 'spec' in request body", 500);
                return;
            }
        } else {
            spec = httpServletRequest.getParameter("spec");
        }
        if (spec == null) {
            error(httpServletResponse, "Missing 'spec' parameter", 500);
            return;
        }

        try {
            tempFileMetadata = doCreatePDFFile(spec, httpServletRequest);
            sendPdfFile(httpServletResponse, tempFileMetadata, Boolean.parseBoolean(httpServletRequest.getParameter("inline")));
        } catch (Throwable e) {
            error(httpServletResponse, e);
        } finally {
            deleteFile(tempFileMetadata.tempFile);
        }
    }

    /**
     * Create the PDF and returns to the client (in JSON) the URL to get the PDF.
     */
    protected void createPDF(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String basePath) throws ServletException {
        TempFileMetadata tempFileMetadata = null;
        try {
            purgeOldTemporaryFiles();

            String spec = getSpecFromPostBody(httpServletRequest);
            tempFileMetadata = doCreatePDFFile(spec, httpServletRequest);
            if (tempFileMetadata == null) {
                error(httpServletResponse, "Missing 'spec' parameter", 500);
                return;
            }
        } catch (Throwable e) {
            deleteFile(tempFileMetadata.tempFile);
            error(httpServletResponse, e);
            return;
        }

        final String id = generateId(tempFileMetadata.tempFile);
        httpServletResponse.setContentType("application/json; charset=utf-8");
        PrintWriter writer = null;
        try {
            writer = httpServletResponse.getWriter();
            JSONWriter json = new JSONWriter(writer);
            json.object();
            {
                json.key("getURL").value(basePath + "/" + id + TEMP_FILE_SUFFIX);
            }
            json.endObject();
        } catch (JSONException e) {
            deleteFile(tempFileMetadata.tempFile);
            throw new ServletException(e);
        } catch (IOException e) {
            deleteFile(tempFileMetadata.tempFile);
            throw new ServletException(e);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
        addTempFileMetaData(tempFileMetadata, id);
    }

    /**
     * Creates a json file to hold the meta-data for the temp file.
     * @param tempFileMetadata Object holding the information regarding the file to be printed
     * @param id identifier for the temp file and is being used to identify the meta data file
     */
    protected void addTempFileMetaData(TempFileMetadata tempFileMetadata, String id) {
        try {
            objectMapper.writeValue(File.createTempFile(TEMP_FILE_METADATA_PREFIX + id + "_",
                    ".json", getTempDir()), tempFileMetadata);
        } catch (IOException e) {
            LOGGER.debug("Unable to persist tempFileMetadata", e);
        }
    }

    protected String getSpecFromPostBody(HttpServletRequest httpServletRequest) throws IOException {
        if (httpServletRequest.getParameter("spec") != null) {
            return httpServletRequest.getParameter("spec");
        }
        BufferedReader data = new BufferedReader(new InputStreamReader(httpServletRequest.getInputStream(), StandardCharsets.UTF_8));

        Closer closer = Closer.create();
        try {
            final InputStreamReader reader = closer.register(new InputStreamReader(httpServletRequest.getInputStream(), getEncoding()));
            BufferedReader bufferedReader = closer.register(new BufferedReader(reader));
            final String spec = CharStreams.toString(bufferedReader);
            return spec;
        } finally {
            closer.close();
        }
    }

    /**
     * Get and cache the used Encoding.
     */
    protected String getEncoding() {
        if (encoding == null) {
            encoding = getInitParameter("encoding");
            LOGGER.debug("Using '" + encoding + "' to encode Inputcontent.");
        }
        if (encoding == null) {
            return "UTF-8";
        } else {
            return encoding;
        }
    }

    /**
     * To get the PDF created previously.
     */
    protected void getFile(HttpServletRequest req, HttpServletResponse httpServletResponse, String id) throws IOException, ServletException {
        TempFileMetadata tempFileMetadata = getTempFileMetadata(id);
        if (tempFileMetadata == null) {
            error(httpServletResponse, "File with id=" + id + " unknown", 404);
            return;
        }
        sendPdfFile(httpServletResponse, tempFileMetadata, Boolean.parseBoolean(req.getParameter("inline")));
    }

    /**
     *
     * @param id identifier to fetch the meta-data json file from the disk.
     * @return
     */
    private TempFileMetadata getTempFileMetadata(String id) {
        TempFileMetadata tempFileMetadata = null;
            // get from disk
            File[] files = getTempDir().listFiles(file -> file.getName().startsWith(TEMP_FILE_METADATA_PREFIX + id + "_"));
            if (0 != files.length) {
                try {
                    tempFileMetadata = objectMapper.readValue(files[0], TempFileMetadata.class);
                } catch (IOException e) {
                    // could be deleted while the reading has not started
                    LOGGER.info("File requested for the id ::" + id + " has been deleted");
                    tempFileMetadata = null;
                }
            }
        return tempFileMetadata;
    }

    /**
     * To get (in JSON) the information about the available formats and CO.
     */
    protected void getInfo(HttpServletRequest req, HttpServletResponse resp, String basePath) throws ServletException, IOException {
        app = req.getParameter("app");
        //System.out.println("app = "+app);

        MapPrinter printer = getMapPrinter(app);
        try {
            resp.setContentType("application/json; charset=utf-8");
            final PrintWriter writer = resp.getWriter();

            try {
                final String var = req.getParameter("var");
                if (var != null) {
                    writer.print(var + "=");
                }

                JSONWriter json = new JSONWriter(writer);
                try {
                    json.object();
                    {
                        printer.printClientConfig(json);
                        String urlToUseInSpec = basePath;

                        String proxyUrl = printer.getConfig().getProxyBaseUrl();
                        if (proxyUrl != null) {
                            urlToUseInSpec = proxyUrl;
                        }
                        json.key("printURL").value(urlToUseInSpec + PRINT_URL);
                        json.key("createURL").value(urlToUseInSpec + CREATE_URL);
                        if (app != null) {
                            json.key("app").value(app);
                        }
                    }
                    json.endObject();
                } catch (JSONException e) {
                    throw new ServletException(e);
                }
                if (var != null) {
                    writer.print(";");
                }
            } finally {
                writer.close();
            }
        } finally {
            if (printer != null) {
                printer.stop();
            }
        }
    }

    /**
     * Do the actual work of creating the PDF temporary file.
     * @throws InterruptedException
     */
    protected TempFileMetadata doCreatePDFFile(String spec, HttpServletRequest httpServletRequest) throws IOException, DocumentException, ServletException, InterruptedException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Generating PDF for spec=" + spec);
        }

        if (SPEC_LOGGER.isInfoEnabled()) {
            SPEC_LOGGER.info(spec);
        }

        PJsonObject specJson = MapPrinter.parseSpec(spec);
        if (specJson.has("app")) {
            app = specJson.getString("app");
        } else {
            app = null;
        }

        MapPrinter mapPrinter = getMapPrinter(app);
        try {
            Map<String, String> headers = new HashMap<String, String>();
            TreeSet<String> configHeaders = mapPrinter.getConfig().getHeaders();
            if (configHeaders == null) {
                configHeaders = new TreeSet<String>();
                configHeaders.add("Referer");
                configHeaders.add("Cookie");
            }
            for (Iterator<String> header_iter = configHeaders.iterator(); header_iter.hasNext(); ) {
                String header = header_iter.next();
                if (httpServletRequest.getHeader(header) != null) {
                    headers.put(header, httpServletRequest.getHeader(header));
                }
            }

            final OutputFormat outputFormat = mapPrinter.getOutputFormat(specJson);
            // create a temporary file that will contain the PDF
            final File tempJavaFile = File.createTempFile(TEMP_FILE_PREFIX,
                    "." + outputFormat.getFileSuffix() + TEMP_FILE_SUFFIX, getTempDir());
            TempFileMetadata tempFileMetadata = new TempFileMetadata(tempJavaFile, specJson, outputFormat);

            FileOutputStream out = null;
            try {
                out = new FileOutputStream(tempFileMetadata.tempFile);
                if (mapPrinter.getConfig().isAddForwardedFor()) {
                    String ipAddress = httpServletRequest.getHeader("X-FORWARDED-FOR");
                    if (ipAddress != null) {
                        String[] ips = ipAddress.split(", ");
                        ipAddress = ips[0];
                    } else {
                        ipAddress = httpServletRequest.getRemoteAddr();
                    }
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Forwarded for: " + ipAddress);
                    }
                    headers.put("X-Forwarded-For", ipAddress);
                }
                mapPrinter.print(specJson, out, headers);

                return tempFileMetadata;
            } catch (IOException e) {
                deleteFile(tempFileMetadata.tempFile);
                throw e;
            } catch (DocumentException e) {
                deleteFile(tempFileMetadata.tempFile);
                throw e;
            } catch (InterruptedException e) {
                deleteFile(tempFileMetadata.tempFile);
                throw e;
            } finally {
                if (out != null) {
                    out.close();
                }
            }
        } finally {
            if (mapPrinter != null) {
                mapPrinter.stop();
            }
        }
    }

    /**
     * copy the PDF into the output stream
     */
    protected void sendPdfFile(HttpServletResponse httpServletResponse, TempFileMetadata tempFileMetadata, boolean inline) throws IOException, ServletException {
        FileInputStream pdf = new FileInputStream(tempFileMetadata.tempFile);
        final OutputStream response = httpServletResponse.getOutputStream();
        MapPrinter mapPrinter = getMapPrinter(app);
        try {
            httpServletResponse.setContentType(tempFileMetadata.contentType());
            if (!inline) {
                final String fileName = tempFileMetadata.getOutputFileName(mapPrinter);
                httpServletResponse.setHeader("Content-disposition", "attachment; filename=" + fileName);
            }
            FileUtilities.copyStream(pdf, response);
        } finally {
            if (mapPrinter != null) {
                mapPrinter.stop();
            }
            try {
                pdf.close();
            } finally {
                response.close();
            }
        }
    }

    /**
     * Send an error XXX to the client with an exception
     */
    protected void error(HttpServletResponse httpServletResponse, Throwable e) {
        PrintWriter out = null;
        try {
            httpServletResponse.setContentType("text/plain");
            httpServletResponse.setStatus(500);
            out = httpServletResponse.getWriter();
            out.println("Error while generating PDF:");
            e.printStackTrace(out);

            LOGGER.error("Error while generating PDF", e);
        } catch (IOException ex) {
            throw new RuntimeException(e);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    /**
     * Send an error XXX to the client with a message
     */
    protected void error(HttpServletResponse httpServletResponse, String message, int code) {
        PrintWriter out = null;
        try {
            httpServletResponse.setContentType("text/plain");
            httpServletResponse.setStatus(code);
            out = httpServletResponse.getWriter();
            out.println("Error while generating PDF:");
            out.println(message);

            LOGGER.error("Error while generating PDF: " + message);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    /**
     * Get and cache the temporary directory to use for saving the generated PDF files.
     */
    protected File getTempDir() {
        if (tempDir == null) {
            String tempDirPath = getInitParameter("tempdir");
            if (tempDirPath == null) {
                tempDirPath = System.getProperty("MAPFISH_PDF_FOLDER");
            }
            if (tempDirPath != null && !"".equals(tempDirPath.trim())) {
                tempDir = new File(tempDirPath);
            } else {
                tempDir = (File) getServletContext().getAttribute(CONTEXT_TEMPDIR);
            }
            if (!tempDir.exists() && !tempDir.mkdirs()) {
                throw new RuntimeException("unable to create dir:" + tempDir);
            }

        }
        LOGGER.debug("Using '" + tempDir.getAbsolutePath() + "' as temporary directory");
        return tempDir;
    }

    /**
     * If the file is defined, delete it.
     */
    protected void deleteFile(File file) {
        if (file != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Deleting PDF file: " + file.getName());
            }
            if (!file.delete()) {
                LOGGER.warn("Cannot delete file:" + file.getAbsolutePath());
            }
        }
    }

    /**
     * Get the ID to use in function of the filename (filename without the prefix and the extension).
     */
    protected String generateId(File tempFile) {
        final String name = tempFile.getName();
        return name.substring(
                TEMP_FILE_PREFIX.length(),
                name.length() - TEMP_FILE_SUFFIX.length());
    }

    protected String getBaseUrl(HttpServletRequest httpServletRequest) {
        final String additionalPath = httpServletRequest.getPathInfo();
        String fullUrl = httpServletRequest.getParameter("url");
        if (fullUrl != null) {
            return fullUrl.replaceFirst(additionalPath + "$", "");
        } else {
            String customUrl = System.getProperty("PRINT_BASE_URL");
            if (customUrl != null && !"".equals(customUrl.trim())) {
                return customUrl.replaceFirst(additionalPath + "$", "");
            } else {
                return httpServletRequest.getRequestURL().toString().replaceFirst(additionalPath + "$", "");
            }
        }
    }

    /**
     * Will purge all the known temporary files older than TEMP_FILE_PURGE_SECONDS.
     */
    protected void purgeOldTemporaryFiles() {
        if (!purging.getAndSet(true)) {
            File[] tempDirFiles = getTempDir().listFiles();
            for (File file : tempDirFiles) {
                try {
                    if (shouldFileBeDelete(file)) {
                        deleteFile(file);
                    }
                } catch (IOException e) {
                    LOGGER.warn("Unable to delete file :: ", e);
                }
            }
        }
        purging.set(false);
    }

    static class TempFileMetadata {
        private static final long serialVersionUID = 455104129549002361L;
        public final String printedLayoutName;
        public final String outputFileName;
        public final String contentType;
        public final File tempFile;
        public String suffix;

        public TempFileMetadata(File tempFile, PJsonObject jsonSpec, OutputFormat format) {
            this.outputFileName = jsonSpec.optString(Constants.OUTPUT_FILENAME_KEY);
            this.printedLayoutName = jsonSpec.optString(Constants.JSON_LAYOUT_KEY, null);
            this.tempFile = tempFile;
            this.suffix = format.getFileSuffix();
            this.contentType = format.getContentType();
        }

        @JsonCreator
        public TempFileMetadata(@JsonProperty("tempFile") File tempFile, @JsonProperty("outputFileName") String outputFileName, @JsonProperty("printedLayoutName") String printedLayoutName,
                @JsonProperty("suffix") String suffix, @JsonProperty("contentType") String contentType) {
            this.outputFileName = outputFileName;
            this.printedLayoutName = printedLayoutName;
            this.tempFile = tempFile;
            this.suffix = suffix;
            this.contentType = contentType;
        }

        public static String formatFileName(String suffix, String startingName, Date date) {
            Matcher matcher = Pattern.compile("\\$\\{(.+?)\\}").matcher(startingName);
            HashMap<String, String> replacements = new HashMap<String, String>();
            while (matcher.find()) {
                String pattern = matcher.group(1);
                String key = "${" + pattern + "}";
                replacements.put(key, findReplacement(pattern, date));
            }
            String result = startingName;
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }

            while (suffix.startsWith(".")) {
                suffix = suffix.substring(1);
            }
            if (suffix.isEmpty() || result.toLowerCase().endsWith("." + suffix.toLowerCase())) {
                return result;
            } else {
                return result + "." + suffix;
            }
        }

        public static String cleanUpName(String original) {
            return original.replace(",", "").replaceAll("\\s+", "_");
        }

        private static String findReplacement(String pattern, Date date) {
            if (pattern.toLowerCase().equals("date")) {
                return cleanUpName(DateFormat.getDateInstance().format(date));
            } else if (pattern.toLowerCase().equals("datetime")) {
                return cleanUpName(DateFormat.getDateTimeInstance().format(date));
            } else if (pattern.toLowerCase().equals("time")) {
                return cleanUpName(DateFormat.getTimeInstance().format(date));
            } else {
                try {
                    return new SimpleDateFormat(pattern).format(date);
                } catch (Exception e) {
                    LOGGER.log(Level.WARN, "Unable to format timestamp according to pattern: ${%s}".formatted(pattern), e);
                    return "${" + pattern + "}";
                }
            }
        }

        public String getOutputFileName(MapPrinter mapPrinter) {
            if (outputFileName != null) {
                return formatFileName(suffix, outputFileName, new Date());
            } else {
                return formatFileName(suffix, mapPrinter.getOutputFilename(printedLayoutName, tempFile.getName()), new Date());
            }
        }

        public String contentType() {
            return contentType;
        }
    }
}
