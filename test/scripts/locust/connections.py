from locust import HttpUser, between, task

# Locust Laodtests
#
# Install: pip3 install locust
# Run: locust -f connections.py
#
# See: https://docs.locust.io/
#
class WebsiteUser(HttpUser):
    # wait_time = between(5, 15)

    @task
    def fruit(self):
        self.client.get("/shop/products/")
