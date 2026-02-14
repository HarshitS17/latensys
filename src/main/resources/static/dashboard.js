let endpointChart = null;

/* Safe Fetch */
async function safeFetch(url) {
    try {
        const res = await fetch(url);
        if (!res.ok) throw new Error("Network error");
        return await res.json();
    } catch (err) {
        console.error("Fetch failed:", err);
        return null;
    }
}

/* ================= GLOBAL METRICS ================= */

async function loadGlobalMetrics() {
    const data = await safeFetch("/monitor/metrics");
    if (!data) return;

    document.getElementById("totalRequests").innerText =
        data.totalRequests;

    document.getElementById("avgResponse").innerText =
        Math.round(data.averageResponseTime) + " ms";
}

/* ================= ERROR RATE ================= */

async function loadErrorRate() {
    const data = await safeFetch("/monitor/errors");
    if (!data) return;

    const rate = data.errorRate.toFixed(2);
    const el = document.getElementById("errorRate");

    el.innerText = rate + "%";

    if (rate > 5) el.style.color = "#ef4444";
    else if (rate > 2) el.style.color = "#facc15";
    else el.style.color = "#22c55e";
}

/* ================= RATE LIMIT ================= */

async function loadRateLimitStats() {
    const data = await safeFetch("/monitor/rate-limit");
    if (!data) return;

    const statusEl = document.getElementById("rateEnabled");
    statusEl.innerText = data.enabled ? "ENABLED" : "DISABLED";
    statusEl.className = data.enabled ? "status enabled" : "status disabled";

    document.getElementById("rateLimit").innerText =
        `${data.limit} requests / ${data.windowSeconds}s`;

    document.getElementById("blockedRequests").innerText =
        data.blockedRequests;

    document.getElementById("blockedMetric").innerText =
        data.blockedRequests;
}

/* ================= CHART ================= */

async function loadEndpointChart() {

    const windowValue =
        document.getElementById("windowSelect").value;

    const data = await safeFetch(
        `/monitor/endpoints?window=${windowValue}`
    );
    if (!data) return;

    const labels = data.map(d => d.uri);
    const values = data.map(d => d.p95Latency);

    if (endpointChart) endpointChart.destroy();

    endpointChart = new Chart(
        document.getElementById("endpointChart"),
        {
            type: "bar",
            data: {
                labels,
                datasets: [{
                    label: `P95 (${windowValue}m)`,
                    data: values,
                    backgroundColor: "#22c55e",
                    borderRadius: 6
                }]
            },
            options: {
                responsive: true,
                plugins: { legend: { display: false } },
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

async function loadAll() {
    await Promise.all([
        loadGlobalMetrics(),
        loadErrorRate(),
        loadRateLimitStats(),
        loadEndpointChart()
    ]);
}

document
    .getElementById("windowSelect")
    .addEventListener("change", loadEndpointChart);

loadAll();

setInterval(loadAll, 5000);