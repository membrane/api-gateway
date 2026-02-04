import groovy.xml.XmlSlurper
import groovy.json.JsonOutput

def root = new XmlSlurper(false, true)
        .parseText(body.toString())

root.declareNamespace(
  s11: "http://schemas.xmlsoap.org/soap/envelope/",
  ns:  "https://predic8.de/shop-service"
)

def resp = root.'s11:Body'.'ns:createProductResponse'

def payload = [
  success : [
    id      : resp.'ns:id'?.text(),
    category: resp.'ns:category'?.text()
  ]
]

header['Content-Type'] = "application/json"
JsonOutput.toJson(payload)
