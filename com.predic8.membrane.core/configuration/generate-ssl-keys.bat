keytool -genkey -alias client -keypass secret -keystore client.jks  -storepass secret -dname "cn=client" 

keytool -selfcert -alias client -keystore client.jks -storepass secret -keypass secret -validity 3650

keytool -export -alias client -file client.cer -keystore client.jks -storepass secret


keytool -genkey -alias membrane -keypass secret -keystore membrane.jks  -storepass secret -dname "cn=membrane" 

keytool -selfcert -alias membrane -keystore membrane.jks -storepass secret -keypass secret -validity 3650

keytool -export -alias membrane -file membrane.cer -keystore membrane.jks -storepass secret

keytool -import -alias membrane -file membrane.cer -keystore client.jks -storepass secret

keytool -import -alias client -file client.cer -keystore membrane.jks -storepass secret