This code is from https://github.com/entrusc/slf4j-osgi
Author : Florian Frankenberger

slf4j-osgi
==========

This code is a OSGi logging service adapter for SLF4J. That means that all things you log are
forwarded to a OSGi LogService present in your OSGi environment (like e.g. apache felix).

building
========

This is a Java library that uses maven to build. Checkout the files of the git repo,
open the checked out folder in NetBeans and hit build. Or if you like to use the command
line just enter:

    mvn install

to build and install the library.

usage
=====

You can then use the resulting .jar in your project along with the normal slf4j lib. Also
this line has to be called in the OSGi Activator:

        org.slf4j.impl.OSGiLogFactory.initOSGi(bundleContext);

so that the adapter can forward the log messages to the LogService.

usage with maven
================

In maven just use the following dependencies:

        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>1.7.2</version>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-osgi</artifactId>
            <version>1.7.2</version>
        </dependency>

and everything should work out of the box.
