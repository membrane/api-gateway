#!/bin/bash

echo "No throttling applied:"
curl -o /dev/null -s -w "Total time: %{time_total}\n" http://localhost:3000
curl -o /dev/null -s -w "Total time: %{time_total}\n" http://localhost:3000
curl -o /dev/null -s -w "Total time: %{time_total}\n" http://localhost:3000
curl -o /dev/null -s -w "Total time: %{time_total}\n" http://localhost:3000
curl -o /dev/null -s -w "Total time: %{time_total}\n" http://localhost:3000
echo "With throttling enabled:"
curl -o /dev/null -s -w "Total time: %{time_total}\n" http://localhost:2000
curl -o /dev/null -s -w "Total time: %{time_total}\n" http://localhost:2000
curl -o /dev/null -s -w "Total time: %{time_total}\n" http://localhost:2000
curl -o /dev/null -s -w "Total time: %{time_total}\n" http://localhost:2000
curl -o /dev/null -s -w "Total time: %{time_total}\n" http://localhost:2000
