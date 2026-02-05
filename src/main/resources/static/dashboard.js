let endpointChart = null;

/* ================= GLOBAL METRICS ================= */

async function loadGlobalMetrics() {
    const res = await fetch("/monitor/metrics");
    const data = await res.json();

    document.getElementById("totalRequests").innerText = data.totalRequests;
    document.getElementById("avgResponse").innerText =
        Math.round(data.averageResponseTime) + " ms";
}

/* ================= ERROR RATE ================= */

async function loadErrorRate() {
    const res = await fetch("/monitor/errors");
    const data = await res.json();

    const rate = data.errorRate.toFixed(2);
    document.getElementById("errorRate").innerText = rate + "%";
}

/* ================= RATE LIMIT ================= */

async function loadRateLimitStats() {
    const res = await fetch("/monitor/rate-limit");
    const data = await res.json();

    document.getElementById("rateEnabled").innerText =
        data.enabled ? "Enabled" : "Disabled";
    document.getElementById("rateLimit").innerText =
        `${data.limit} requests / ${data.windowSeconds}s`;
    document.getElementById("blockedRequests").innerText =
        data.blockedRequests;
}

/* ================= P95 ENDPOINT CHART ================= */

async function loadEndpointChart() {
    const window = document.getElementById("windowSelect").value;

    const res = await fetch(`/monitor/endpoints?window=${window}`);
    const data = await res.json();

    const labels = data.map(d => d.uri);
    const values = data.map(d => d.p95Latency);

    if (endpointChart) {
        endpointChart.destroy();
    }

    endpointChart = new Chart(
        document.getElementById("endpointChart"),
        {
            type: "bar",
            data: {
                labels,
                datasets: [{
                    label: `P95 Latency (last ${window} min)`,
                    data: values,
                    backgroundColor: "#ff5c5c",
                    borderRadius: 6
                }]
            },
            options: {
                responsive: true,
                plugins: {
                    legend: { display: false }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            callback: v => v + " ms"
                        }
                    }
                }
            }
        }
    );
}

/* ================= INIT ================= */

loadGlobalMetrics();
loadErrorRate();
loadRateLimitStats();
loadEndpointChart();

/* ================= EVENTS ================= */

document
    .getElementById("windowSelect")
    .addEventListener("change", loadEndpointChart);

/* ================= AUTO REFRESH ================= */

setInterval(() => {
    loadGlobalMetrics();
    loadErrorRate();
    loadRateLimitStats();
    loadEndpointChart();
}, 5000);