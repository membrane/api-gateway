# Step 30 of the Docker tutorial, see 30-Own-Image.md
#
# Build: docker build -t my-gateway .
FROM predic8/membrane:7.8.1

# Replaces apis.yaml and log4j2.xml of the image, keeps its other files in conf/
COPY conf/ /opt/membrane/conf/

# No ENTRYPOINT: the one of the base image runs Membrane under tini,
# so docker stop shuts Membrane down gracefully instead of killing it after 10 seconds.
