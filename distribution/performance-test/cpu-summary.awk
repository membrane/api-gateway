# Include only complete sampling intervals inside the client's measured window.
# Input: epoch_ms user nice system idle iowait irq softirq steal.
NF == 9 {
    if (havePrevious && previous >= start && $1 <= end && $1 > previous) {
        valid = 1
        for (i = 2; i <= 9; i++) {
            delta[i] = $i - last[i]
            if (delta[i] < 0) valid = 0
        }
        if (valid) {
            for (i = 2; i <= 9; i++) sum[i] += delta[i]
            samples++
        }
    }
    previous = $1
    havePrevious = 1
    for (i = 2; i <= 9; i++) last[i] = $i
}
END {
    for (i = 2; i <= 9; i++) total += sum[i]
    if (total > 0)
        printf "n=%d busy=%.1f usr=%.1f sys=%.1f soft=%.1f idle=%.1f\n", \
            samples, 100 * (total - sum[5]) / total, 100 * sum[2] / total, \
            100 * sum[4] / total, 100 * sum[8] / total, 100 * sum[5] / total
    else
        print "No complete CPU sampling intervals inside measured window"
}
