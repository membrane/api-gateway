curl -X POST http://localhost:2000/city-service \
  -H "Content-Type: text/xml" \
  -H "SOAPAction: https://predic8.de/cities" \
  -d @- <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<s11:Envelope
    xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:cs="https://predic8.de/cities">
  <s11:Body>
    <cs:getCity>
      <name>Berlin</name>
    </cs:getCity>
  </s11:Body>
</s11:Envelope>
EOF