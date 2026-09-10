<?xml version="1.0" encoding="UTF-8"?>
<schema xmlns="http://www.ascc.net/xml/schematron">
    <pattern name="Order">
        <rule context="order">
            <assert test="count(item) &gt; 0">An order must contain at least one item.</assert>
        </rule>
    </pattern>
</schema>
