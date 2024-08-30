# Sending Requests to multiple targets using \<parallel>

Using the `ParallelInterceptor` you can send requests to multiple targets. The returned response depends on the chosen strategy. There are multiple strategies to chose from:

| Example                            | Description                                                                                                                                          |
|------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| [Traffic Shadowing](shadowing)     | Declare a primary target. The response from this primary target is returned immediately, while requests to other targets are sent in the background. |
| [Custom Strategy](custom-strategy) | Define a custom strategy using `SpEL`.                                                                                                               |
