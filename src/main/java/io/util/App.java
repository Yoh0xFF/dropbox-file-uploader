package io.util;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.CommitInfo;
import com.dropbox.core.v2.files.UploadSessionCursor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;

public class App {

  private static final Logger logger = LogManager.getLogger();
  private static final int BUFFER_SIZE = 1024 * 1024 * 100; // 100MB

  public static void main(String[] args) {
    var dbxAccessToken = System.getenv("DBX_ACCESS_TOKEN");
    if (isBlank(dbxAccessToken)) {
      logger.error("Please set DBX_ACCESS_TOKEN environment variable");
      return;
    }

    var dbxClientName = System.getenv("DBX_CLIENT_NAME");
    if (isBlank(dbxClientName)) {
      logger.error("Please set DBX_CLIENT_NAME environment variable");
      return;
    }

    if (args.length == 0) {
      logger.error("Please pass file path");
      return;
    }

    // Create Dropbox client
    var dbcRequestConfig = DbxRequestConfig.newBuilder(dbxClientName).withUserLocale("en_US").build();
    var dbxClient = new DbxClientV2(dbcRequestConfig, dbxAccessToken);
    var dbxFileMngr = dbxClient.files();

    // create folder in dropbox
    var curDate = LocalDate.now();
    var curYear = "" + curDate.getYear();
    var curMonth = curDate.getMonthValue() < 10 ? "0" + curDate.getMonthValue() : "" + curDate.getMonthValue();
    var dbxFolder = "/" + curYear + "/" + curMonth;

    var folderExists = true;
    try {
      dbxFileMngr.getMetadata(dbxFolder);
    } catch (DbxException ex) {
      folderExists = false;
    }

    if (!folderExists) {
      try {
        dbxFileMngr.createFolderV2(dbxFolder);
      } catch (DbxException ex) {
        logger.error("Folder creation failed", ex);
        return;
      }
    }

    // Upload files to Dropbox
    for (var filePath : args) {
      logger.info("Start file upload: {}", filePath);

      var file = new File(filePath);
      var dbxFilePath = dbxFolder + "/" + file.getName();
      try {
        if (file.length() <= BUFFER_SIZE) {
          try (InputStream in = new FileInputStream(file)) {
            dbxFileMngr.uploadBuilder(dbxFilePath).uploadAndFinish(in);
          }
        } else {
          var buffer = new byte[BUFFER_SIZE];
          int k;
          var offset = 0;
          var sessionId = "";

          try (var in = new FileInputStream(file)) {
            var firstRead = true;
            var lastRead = false;
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
                  UploadSessionCursor cursor = new UploadSessionCursor(sessionId, offset);
                  dbxFileMngr.uploadSessionAppendV2(cursor).uploadAndFinish(bis);
                }
              }

              offset += k;
            }
          }
        }

        logger.info("File upload finished successfully: {}", filePath);
      } catch (DbxException | IOException ex) {
        logger.error("File upload failed", ex);
      }
    }
  }

  private static boolean isBlank(final CharSequence cs) {
    final var strLen = (cs == null ? 0 : cs.length());
    if (strLen == 0) {
      return true;
    }
    for (var i = 0; i < strLen; i++) {
      if (!Character.isWhitespace(cs.charAt(i))) {
        return false;
      }
    }
    return true;
  }

}
