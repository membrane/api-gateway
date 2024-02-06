%global basedir %{_var}/lib/%{name}
%global confdir %{_sysconfdir}/%{name}
%global homedir %{_datadir}/%{name}
%global logdir %{_var}/log/%{name}

Name:             membrane
Version:          5.3.1
Release:          1%{?dist}
URL:              https://github.com/membrane/api-gateway
Summary:          Membrane - Open Source API Gateway written in Java for REST APIs, WebSockets, STOMP and legacy Web Services
License:          Membrane
Group:            System
Packager:         predic8 GmbH
BuildRoot:        %{_tmppath}/build-%{name}-%{version}
Requires:         java-headless >= 17
Requires(pre):    %{_sbindir}/useradd, %{_sbindir}/groupadd
Requires(post):   systemd
Requires(preun):  systemd
Requires(postun): systemd
BuildRequires:    wget
BuildRequires:    unzip
BuildRequires:    systemd
%{?systemd_requires}

%define zip_name membrane-api-gateway-%{version}

%define debug_package %{nil}
%define __jar_repack %{nil}

%description
%{summary}

%prep
%setup -q -c -T
wget %{url}/releases/download/v%{version}/%{zip_name}.zip
unzip %{zip_name}.zip

%{__cat} <<EOF > %{name}.service
[Unit]
Description=Membrane - API Gateway
After=network.target remote-fs.target nss-lookup.target syslog.target

[Service]
Type=simple
User=%{name}
Group=%{name}
ExecStart=%{_datadir}/%{name}/service-proxy.sh -c /%{confdir}/proxies.xml
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

%{__cat} <<EOF > proxies.xml
<!--
    Membrane Configuration Reference:
     - https://membrane-soa.org/api-gateway-doc/current/configuration/reference/
     - https://www.membrane-soa.org/api-gateway-doc/current/configuration/samples.html
-->
<spring:beans xmlns="http://membrane-soa.org/proxies/1/"
  xmlns:spring="http://www.springframework.org/schema/beans"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.2.xsd
                      http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd">
  <router hotDeploy="false">

    <!-- <api port="2000">
      <target url="https://api.predic8.de" />
    </api> -->

  </router>
</spring:beans>
EOF

%{__cat} <<EOF > log4j2.xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration>
  <Appenders>
    <Console name="STDOUT" target="SYSTEM_OUT">
      <PatternLayout pattern="%d %5p %tid %tn %c{1}:%L - %m%n" />
    </Console>
    <!-- <RollingFile name="FILE" fileName="%{logdir}/membrane.log"
      filePattern="%{logdir}membrane.log.%i">
      <PatternLayout pattern="%d %5p %tid %tn %c{1}:%L - %m%n" />
      <Policies>
        <SizeBasedTriggeringPolicy size="100 MB" />
      </Policies>
      <DefaultRolloverStrategy max="10" />
    </RollingFile> -->
  </Appenders>
  <Loggers>
    <!-- <Logger name="com.predic8.membrane.core.openapi" level="debug">
      <AppenderRef ref="FILE" />
    </Logger>
    <Logger name="com.predic8" level="info">
      <AppenderRef ref="FILE" />
    </Logger> -->
    <Root level="info">
      <AppenderRef ref="STDOUT" />
    </Root>
  </Loggers>
</Configuration>
EOF

%pre
%{_bindir}/getent group %{name} > /dev/null || %{_sbindir}/groupadd -r %{name}
%{_bindir}/getent passwd %{name} > /dev/null || \
    %{_sbindir}/useradd -r -d %{basedir} -g %{name} \
    -s %{_sbindir}/nologin -c "%{summary}" %{name}
exit 0

%install
%{__install} -d -m 0775 %{buildroot}%{confdir}
%{__install} -d -m 0775 %{buildroot}%{homedir}
%{__install} -d -m 0775 %{buildroot}%{logdir}
%{__install} -d -m 0755 %{buildroot}%{_unitdir}

%{__cp} -r %{zip_name}/* %{buildroot}%{homedir}

%{__install} -D -m 0644 %{name}.service %{buildroot}%{_unitdir}

%{__install} -D -m 0640 proxies.xml %{buildroot}%{confdir}
%{__install} -D -m 0640 log4j2.xml %{buildroot}%{confdir}

%{__rm} -rf %{buildroot}%{homedir}/conf
%{__rm} -rf %{buildroot}%{homedir}/examples
%{__rm} -rf %{buildroot}%{homedir}/tutorials

%{__rm} -f %{buildroot}%{homedir}/service-proxy.bat
%{__rm} -f %{buildroot}%{homedir}/CHANGELOG.txt
%{__rm} -f %{buildroot}%{homedir}/INSTALL_TANUKI.txt
%{__rm} -f %{buildroot}%{homedir}/README.txt
%{__rm} -f %{buildroot}%{homedir}/build-install-wrapper.xml

# TODO use %{confdir} macro here, does not get replaced currently
sed -i 's#CLASSPATH="$MEMBRANE_HOME/conf"#CLASSPATH="/etc/membrane"#' %{buildroot}%{homedir}/service-proxy.sh

%post
%systemd_post %{name}.service

%preun
%systemd_preun %{name}.service

%postun
%systemd_postun_with_restart %{name}.service

%clean
%__rm -rf "%{buildroot}"

%files
%defattr(0664,root,%{name},0755)
%attr(0644,root,root) %{_unitdir}/%{name}.service
%attr(0755,root,%{name}) %dir %{confdir}

%defattr(0664,%{name},root,0770)
%attr(0770,%{name},root) %dir %{logdir}

%defattr(0640,root,%{name},0775)
%config(noreplace) %{confdir}/proxies.xml
%config(noreplace) %{confdir}/log4j2.xml

%defattr(-,root,%{name})
%{homedir}

%changelog
* Mon Sep 18 2023 predic8 <info@predic8.de>
- Updated Membrane version to 5.2.0
* Thu Mar 16 2023 predic8 <info@predic8.de>
- initial example
