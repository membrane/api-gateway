/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.discovery;

import java.time.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.discovery.CatalogEntry.ProxyKind.*;

/**
 * Catalogs the renderer tests share, so that each test asserts on the rendering rather than on the
 * collecting.
 */
class CatalogTestFixtures {

    static final LocalDate DAY = LocalDate.of(2026, 1, 15);

    static final CollectionInfo COLLECTION =
            new CollectionInfo("Gateway APIs", "Every API of this gateway");

    static Catalog catalog(CatalogEntry... apis) {
        return new Catalog("example.com", "https://example.com/.well-known/api-catalog", List.of(apis));
    }

    /**
     * An API described by an OpenAPI document, so it has a version, documentation links and tags.
     */
    static CatalogEntry describedApi() {
        return describedApi(CatalogEntry.Details.NONE);
    }

    static CatalogEntry describedApi(CatalogEntry.Details details) {
        return CatalogEntry.builder()
                .aid("example.com:fruit-shop-api-v2-0-0")
                .id("fruit-shop-api-v2-0-0")
                .kind(API)
                .name("Fruit Shop API")
                .description("Showcases REST API design")
                .version("2.0.0")
                .endpoint(new CatalogEntry.Endpoint("https", "example.com", 443, "/shop/v2/", "*",
                        "https://example.com/shop/v2/"))
                .links(new CatalogEntry.Links(
                        "https://example.com/api-docs/ui/fruit-shop-api-v2-0-0",
                        "https://example.com/api-docs/fruit-shop-api-v2-0-0"))
                .tags(List.of("Products", "Orders"))
                .contact(new CatalogEntry.Contact("Predic8", "info@predic8.de", "https://www.predic8.de"))
                .license(new CatalogEntry.License("Apache-2.0",
                        "https://www.apache.org/licenses/LICENSE-2.0"))
                .termsOfService("https://example.com/terms")
                .details(details)
                .build();
    }

    /**
     * A plain proxy: no OpenAPI document, so no version and no links.
     */
    static CatalogEntry plainProxy() {
        return CatalogEntry.builder()
                .aid("example.com:shop")
                .id("shop")
                .kind(SERVICE_PROXY)
                .name("Shop")
                .description("Shop")
                .endpoint(new CatalogEntry.Endpoint("http", "example.com", 2000, "/shop", "*",
                        "http://example.com:2000/shop"))
                .build();
    }

    /**
     * An api that publishes a catalog itself, here in the APIs.json format.
     */
    static CatalogEntry apisJsonCatalog() {
        return CatalogEntry.builder()
                .aid("example.com:apis-json")
                .id("apis-json")
                .kind(API)
                .name("APIs.json")
                .description("APIs.json")
                .endpoint(new CatalogEntry.Endpoint("https", "example.com", 443, "/apis.json", "*",
                        "https://example.com/apis.json"))
                .catalogType("application/json")
                .build();
    }

    static CatalogEntry soapProxy() {
        return CatalogEntry.builder()
                .aid("example.com:cities")
                .id("cities")
                .kind(SOAP_PROXY)
                .name("CityService")
                .description("CityService")
                .endpoint(new CatalogEntry.Endpoint("http", "example.com", 2000, "/cities", "*",
                        "http://example.com:2000/cities"))
                .soap(new CatalogEntry.SoapInfo("http://example.com:2000/cities?wsdl", "CityService",
                        "CityPort"))
                .build();
    }
}
