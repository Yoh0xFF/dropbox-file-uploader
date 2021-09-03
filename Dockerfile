# Use an official ubuntu image as a parent image
FROM openjdk:16

# Install unzipn program
RUN microdnf install unzip

# Set the working directory to /app
WORKDIR /app

# Copy the current directory contents into the container at /deploy
COPY target/dropbox-file-uploader.zip /app/

# Unzip application
RUN unzip /app/dropbox-file-uploader.zip

# Dropbox credentials
ENV DBX_CLIENT_NAME ''
ENV DBX_ACCESS_TOKEN ''

# Run application
ENTRYPOINT ["/bin/bash", "/app/dropbox-file-uploader/run.sh"]