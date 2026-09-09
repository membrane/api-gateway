import groovy.xml.XmlSlurper
import groovy.json.JsonOutput

def root = new XmlSlurper(false, true).parseText(body.toString())

def fault = root.'**'.find { it.name()?.toString()?.endsWith('Fault') }

def payload = [
  error: [
    code   : fault.faultcode.text(),
    message: fault.faultstring.text()
  ]
]

header['Content-Type'] = "application/json"
JsonOutput.toJson(payload)
