---
type: feature
area: Plugins
---
- `xml2Json` and `json2Xml`: the new `xmlSchema` attribute takes an XML Schema (XSD) that decides the mapping instead of heuristics. Values are typed as the schema declares them, a repeatable element is always an array, `xsi:nil` becomes `null`, and `json2Xml` writes the elements in schema order and namespace. Attributes become `@`-prefixed keys. A document whose root element the schema does not declare is rejected with `400` (request) or `500` (response). See `tutorials/xml/15-JSON-to-XML-with-XSD.yaml` and `tutorials/xml/25-XML-to-JSON-with-XSD.yaml`.
