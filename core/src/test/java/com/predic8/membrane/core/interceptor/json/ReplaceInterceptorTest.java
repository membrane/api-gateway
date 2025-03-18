/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReplaceInterceptorTest {

    private ReplaceInterceptor replaceInterceptor;
    private Message msg;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws URISyntaxException {
        replaceInterceptor = new ReplaceInterceptor();
        msg = Request.get("/foo").buildExchange().getRequest();
    }

    @ParameterizedTest
    @MethodSource("jsonReplacementProvider")
    void testReplaceWithJsonPath(String originalJson, String jsonPath, String replacement, String expectedJson) throws IOException {
        msg.setBodyContent(originalJson.getBytes());
        assertEquals(
                objectMapper.readTree(expectedJson),
                objectMapper.readTree(replaceInterceptor.replaceWithJsonPath(
                        msg,
                        jsonPath,
                        replacement)
                )
        );
    }

    private static Stream<Arguments> jsonReplacementProvider() {
        return Stream.of(
                Arguments.of(
                        """
                        {
                            "name": "John",
                            "age": 30
                        }
                        """,
                        "$.name",
                        "Jane",
                        """
                        {
                            "name": "Jane",
                            "age": 30
                        }
                        """
                ),
                Arguments.of(
                        """
                        {
                            "person": {
                                "name": "John",
                                "age": 30
                            }
                        }
                        """,
                        "$.person.name",
                        "Jane",
                        """
                        {
                            "person": {
                                "name": "Jane",
                                "age": 30
                            }
                        }
                        """
                ),
                Arguments.of(
                        """
                        {
                            "people": [
                                {
                                    "name": "John"
                                },
                                {
                                    "name": "Doe"
                                }
                            ]
                        }
                        """,
                        "$.people[0].name",
                        "Jane",
                        """
                        {
                            "people": [
                                {
                                    "name": "Jane"
                                },
                                {
                                    "name": "Doe"
                                }
                            ]
                        }
                        """
                ),
                Arguments.of(
                        """
                        {
                            "family": {
                                "parents": [
                                    {
                                        "name": "John"
                                    },
                                    {
                                        "name": "Doe"
                                    }
                                ]
                            }
                        }
                        """,
                        "$.family.parents[1].name",
                        "Jane",
                        """
                        {
                            "family": {
                                "parents": [
                                    {
                                        "name": "John"
                                    },
                                    {
                                        "name": "Jane"
                                    }
                                ]
                            }
                        }
                        """
                ),
                Arguments.of(
                        """
                        {
                            "employees": [
                                {
                                    "name": "John",
                                    "role": "Manager"
                                },
                                {
                                    "name": "Doe",
                                    "role": "Developer"
                                }
                            ]
                        }
                        """,
                        "$.employees[*].name",
                        "Jane",
                        """
                        {
                            "employees": [
                                {
                                    "name": "Jane",
                                    "role": "Manager"
                                },
                                {
                                    "name": "Jane",
                                    "role": "Developer"
                                }
                            ]
                        }
                        """
                ),
                Arguments.of(
                        """
                        {
                            "company": {
                                "employees": [
                                    {
                                        "name": "John",
                                        "department": {
                                            "name": "HR"
                                        }
                                    },
                                    {
                                        "name": "Doe",
                                        "department": {
                                            "name": "IT"
                                        }
                                    }
                                ]
                            }
                        }
                        """,
                        "$.company.employees[*].department.name",
                        "Operations",
                        """
                        {
                            "company": {
                                "employees": [
                                    {
                                        "name": "John",
                                        "department": {
                                            "name": "Operations"
                                        }
                                    },
                                    {
                                        "name": "Doe",
                                        "department": {
                                            "name": "Operations"
                                        }
                                    }
                                ]
                            }
                        }
                        """
                )
        );
    }


}
