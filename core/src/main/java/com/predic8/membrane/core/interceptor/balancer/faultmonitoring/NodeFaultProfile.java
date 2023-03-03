/* Copyright 2015 Fabian Kessler, Optimaize

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.balancer.faultmonitoring;

/**
 * Contains information about recent service failures of a node.
 *
 * <p>A node that has no profile means there was no failure recently.</p>
 *
 * <p>It is minimalistic, having only a most recent timestamp field and a computed score value.</p>
 *
 * @author Fabian Kessler / Optimaize
 */
class NodeFaultProfile {

    private volatile long lastFailureTimestamp;

    /**
	 * Volatile so that updates are seen instantly by other threads.
	 * Range [0,1] where 1 is perfect (profile will be removed) and close to 0 is totally dead.
	 */
    private volatile double score;

    public NodeFaultProfile(long lastFailureTimestamp) {
        this.lastFailureTimestamp = lastFailureTimestamp;
        this.score = 0.5d; //it starts with a 1, and the first failure already cuts away half.
    }

    /**
	 * @return true if the destination is cleared from bad history, meaning it has had enough consecutive successes to forgive it
	 * for the failures in the past.
	 */
    public boolean informSuccess(long timestamp) {
        if (score >= 0.9d) {
            //this check and set are not atomic. again, we don't care. worst case is that between the
            //2 calls there is a bad one coming in, and the score has been reduced, and we override it to 1d.
            //the chance is low, and it's worth taking this compared to having to synchronize.
            score = 1d;
            return true;
        } else {
            //same here, not atomic, another write could happen in between. but it's fine.
            double newScore = score + 0.1d;
            if (newScore >= 1d) {
                score = 1d;
                return true;
            } else {
                score = newScore;
                return false;
            }
        }
    }

    public void informFailure(long timestamp) {
        lastFailureTimestamp = timestamp;

        //this operation is known to be non-atomic. we don't care, nothing bad happens mathematically.
        score = score / 2d;
    }

    /**
	 * Returns the current success score.
	 * The mathematical formula for computing it is not strictly defined as of now.
	 * Just put the number in relation to others to compute the chance of selecting this destination.
	 *
	 * @return guaranteed range [0,1]
	 */
    public double getScore() {
        double ret = score;
        if (ret > 1d) {
            //this should never happen.
            return 1d;
        }
        return Math.max(ret, 0d);
    }

    /**
	 * @return The time of the event when there was a failure last. Guaranteed to be set, because there was at
	 * least one failure.
	 */
    public long getLastFailureTimestamp() {
        return lastFailureTimestamp;
    }
}
