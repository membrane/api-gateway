This is for version 4.0-12

if you want to change the version look DEBIAN/control  -> Version:

put the files on opt/membrane_service_proxy
for md5sums
mv md5creater.sh to membrane_service_proxy
run
md5creater.sh

mv  md5creater.sh ..
 
run
dpkg-deb -b membrane-service-proxy/ membrane-service-proxy_4.0-12.deb


membrane-service-proxy_4.0-12.deb