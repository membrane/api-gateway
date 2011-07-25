Membrane Router 
======================================================================================================
Thanks for downloading Membrane Router.



Getting Started
======================================================================================================
Make sure you have installed Java 1.6 or higher. Then do the following:

On Windows:

Doubleclick on <install-path>\membrane.bat

or with command line

cd <install-path>
membrane.bat

On Linux:

cd <install-path>
membrane.sh

After startup the router reads the file conf/rules.xml containing some sample routing rules.

<configuration>
	<rules>
		<proxy-rule name="HTTP proxy" port="9000" />
		<forwarding-rule name="thomas-bayer.com" host="*"
			port="3000" path=".*" method="*">
			<targetport>80</targetport>
			<targethost>thomas-bayer.com</targethost>
		</forwarding-rule>
		<forwarding-rule name="localhost" host="*" port="2000" path=".*"
			method="*">
			<targetport>8080</targetport>
			<targethost>localhost</targethost>
		</forwarding-rule>
	</rules>
</configuration>

Excecute membrane.bat -h to see the available command line options.



Latest Version
======================================================================================================
You can find the latest version at

http://www.membrane-soa.org/downloads/



Documentation
======================================================================================================
for information about Membrane Router see

http://www.membrane-soa.org/soap-router.htm



Support
======================================================================================================
Any problem with this release can be reported to 

info@predic8.de



Licensing
======================================================================================================



Enjoy using Membrane!

The Membrane Team
http://membrane-soa.org/


