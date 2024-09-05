package com.predic8.membrane.core.interceptor.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
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
    private Exchange exc;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws URISyntaxException {
        replaceInterceptor = new ReplaceInterceptor();
        exc = Request.get("/foo").buildExchange();
    }

    @ParameterizedTest
    @MethodSource("jsonReplacementProvider")
    void testReplaceWithJsonPath(String originalJson, String jsonPath, String replacement, String expectedJson) throws IOException {
        exc.getRequest().setBodyContent(originalJson.getBytes());
        assertEquals(
                objectMapper.readTree(expectedJson),
                objectMapper.readTree(replaceInterceptor.replaceWithJsonPath(
                        exc,
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