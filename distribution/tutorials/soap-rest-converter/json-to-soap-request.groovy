import groovy.json.JsonSlurper
import groovy.xml.MarkupBuilder

final String S11_NS = "http://schemas.xmlsoap.org/soap/envelope/"
final String NS     = "https://predic8.de/shop-service"

def json = new JsonSlurper().parseText(body.toString())

def sw = new StringWriter()
def xml = new MarkupBuilder(sw)
xml.'s11:Envelope'('xmlns:s11': S11_NS, 'xmlns:ns': NS) {
  's11:Body' {
    "ns:createProduct" {
      'ns:name'(json.name)
      'ns:price'(json.price)
    }
  }
}

header['Content-Type'] = "text/xml; charset=utf-8"
header['SOAPAction'] = "https://predic8.de/create-product"

// Return message body
sw.toString().getBytes("UTF-8")