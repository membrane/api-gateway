ls -1 opt/membrane_service_proxy/lib/* | while read name; do md5sum  "$name"; done > DEBIAN/md5sums

#find . -type f -exec md5sum {} \;
