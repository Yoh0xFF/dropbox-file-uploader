package util;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.CommitInfo;
import com.dropbox.core.v2.files.UploadSessionCursor;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
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

        for (String filePath : args) {
            // Create Dropbox client
            DbxRequestConfig dbcRequestConfig = new DbxRequestConfig(dbxClientName, "en_US");
            DbxClientV2 dbxClient = new DbxClientV2(dbcRequestConfig, dbxAccessToken);

            LOGGER.info("Start file upload: {}", filePath);

            // Upload file to Dropbox
            File file = new File(filePath);
            try {
                if (file.length() <= BUFFER_SIZE) {
                    try (InputStream in = new FileInputStream(file)) {
                        dbxClient.files().uploadBuilder("/" + file.getName()).uploadAndFinish(in);
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
                                    sessionId = dbxClient.files().uploadSessionStart().uploadAndFinish(bis).getSessionId();
                                    firstRead = false;
                                } else if (lastRead) {
                                    UploadSessionCursor cursor = new UploadSessionCursor(sessionId, offset);
                                    CommitInfo commitInfo = new CommitInfo("/" + file.getName());
                                    dbxClient.files().uploadSessionFinish(cursor, commitInfo).uploadAndFinish(bis);
                                } else {
                                    dbxClient.files().uploadSessionAppend(sessionId, offset).uploadAndFinish(bis);
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
