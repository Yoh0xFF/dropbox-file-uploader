# dropbox-file-uploader

Utility to upload file in dropbox using dropbox api

Run program using docker as follows: 
```shell
docker run \
--env DBX_ACCESS_TOKEN=${token} \
--env DBX_CLIENT_NAME=${name} \
-v ${local_dir}:/files \
--rm dropbox-file-uploader /files/file-to-upload.tar.gz
```
