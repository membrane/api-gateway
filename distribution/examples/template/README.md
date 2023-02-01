# Template Plugin

* With the `Template` plugin you create request and responses bodies using a template.
* In the templates variable placeholders are filled with values from the `Exchange` object.
* The `template` plugin supports groovy's `XmlTemplateEngine` and `StreamingTemplateEngine`. 

More information can be found below.

https://docs.groovy-lang.org/docs/next/html/documentation/template-engines.html


## Running the Example

To run the example execute the following steps:

1. Go to the `examples/template` directory.

2. Execute `service-proxy.sh` or `service-proxy.bat`

3. Run this command: 

   `curl -d @jobs.xml http://localhost:2050 -H "Content-Type: application/xml"`

4. You should see a message that says:

   `Dear John Doe, unfortunately our service is currently not available`.


## How it is done

The following part describes the example in detail.  

First take a look at the `proxies.xml` file.

```xml
<serviceProxy name="echo" port="2050">
    <request>
        <xpathExtractor>
            <property name="name" xpath="jobs/@user"/>
        </xpathExtractor>
        <template>Dear ${name}, unfortunately our service is not available at the moment.</template>
    </request>
    <response>
        <groovy>
            exc.setResponse(Response.ok(exc.request.getBodyAsStringDecoded()).build())
            RETURN
        </groovy>
    </response>
    <target url="http://localhost:5000/hello"/>
</serviceProxy>
```
In the `proxies.xml` file you can see `xpathExtractor` plugin. Using the property tag `xpathExtractor` it retrieves a value of `name` attribute from the request using XPath and puts it inside the `Exchange` object

```xml
<?xml version='1.0' encoding='utf-8'?>

<jobs
        token="MTMzNzEzMzcxMzM3MTMzNzEzMzcxMzM3MTMzNzEzMzc="
        user="John Doe"
>
    <job type="execute">
        <info>Preparation</info>
        <path>John/long_work.txt</path>
    </job>
    <job type="all">
        <info>Presentation</info>
        <path>John/not_so_long_work.txt</path>
    </job>
</jobs>
```
Using the value in `Exchange` the `Template` plugin fills the placeholders.
```xml
<template>Dear ${name}, unfortunately our service is currently not available.</template>
```
Notice that the name of variable in the template should be same as the name in property tag.

You can also set template from a file. Like in example below.

```xml
<template contentType="text/plain" location="./template.txt" />
```