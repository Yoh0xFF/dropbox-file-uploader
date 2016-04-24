package util;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.CommitInfo;
import com.dropbox.core.v2.files.DbxUserFilesRequests;
import com.dropbox.core.v2.files.UploadSessionCursor;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Properties;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class App {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String CONFIG_FILE_PATH = "/etc/dropbox-file-uploader/app.properties";
    private static final int BUFFER_SIZE = 1024 * 1024 * 100; // 100MB

    public static void main(String[] args) {
        Properties config = new Properties();
        try (InputStream in = new FileInputStream(CONFIG_FILE_PATH)) {
            config.load(in);
        } catch (IOException ex) {
            LOGGER.error("Please create configuration file \"/etc/dropbox-file-uploader/app.properties\"");
            return;
        }

        String dbxAccessToken = config.getProperty("DBX_ACCESS_TOKEN");
        String dbxClientName = config.getProperty("DBX_CLIENT_NAME");

        if (args.length == 0) {
            LOGGER.error("Please pass file path");
            return;
        }

        // Create Dropbox client
        DbxRequestConfig dbcRequestConfig = new DbxRequestConfig(dbxClientName, "en_US");
        DbxClientV2 dbxClient = new DbxClientV2(dbcRequestConfig, dbxAccessToken);
        DbxUserFilesRequests dbxFileMngr = dbxClient.files();

        // create folder in dropbox
        LocalDate curDate = LocalDate.now();
        String curYear = "" + curDate.getYear();
        String curMonth = StringUtils.leftPad("" + curDate.getMonthValue(), 2, "0");
        String dbxFolder = "/" + curYear + "/" + curMonth;
        
        boolean folderExists = true;
        try {
            dbxFileMngr.getMetadata(dbxFolder);
        } catch (DbxException ex) {
            folderExists = false;
        }

        if (!folderExists) {
            try {
                dbxFileMngr.createFolder(dbxFolder);
            } catch (DbxException ex) {
                LOGGER.error("Folder creation failed", ex);
                return;
            }
        }

        // Upload files to Dropbox
        for (String filePath : args) {
            LOGGER.info("Start file upload: {}", filePath);
            
            File file = new File(filePath);
            String dbxFilePath = dbxFolder + "/" + file.getName();
            try {
                if (file.length() <= BUFFER_SIZE) {
                    try (InputStream in = new FileInputStream(file)) {
                        dbxFileMngr.uploadBuilder(dbxFilePath).uploadAndFinish(in);
                    }
                } else {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int k;
                    long offset = 0;
                    String sessionId = "";

                    try (InputStream in = new FileInputStream(file)) {
                        boolean firstRead = true, lastRead = false;
                        while ((k = in.read(buffer)) != -1) {
                            if (k < BUFFER_SIZE) {
                                lastRead = true;
                            }

                            try (ByteArrayInputStream bis = new ByteArrayInputStream(buffer, 0, k)) {
                                if (firstRead) {
                                    sessionId = dbxFileMngr.uploadSessionStart().uploadAndFinish(bis).getSessionId();
                                    firstRead = false;
                                } else if (lastRead) {
                                    UploadSessionCursor cursor = new UploadSessionCursor(sessionId, offset);
                                    CommitInfo commitInfo = new CommitInfo(dbxFilePath);
                                    dbxFileMngr.uploadSessionFinish(cursor, commitInfo).uploadAndFinish(bis);
                                } else {
                                    dbxFileMngr.uploadSessionAppend(sessionId, offset).uploadAndFinish(bis);
                                }
                            }

                            offset += k;
                        }
                    }
                }

                LOGGER.info("File upload finished successfully: {}", filePath);
            } catch (DbxException | IOException ex) {
                LOGGER.error("File upload failed", ex);
            }
        }
    }
}
