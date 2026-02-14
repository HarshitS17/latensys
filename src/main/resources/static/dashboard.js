/* ================= GLOBAL CHART REFERENCES ================= */

let endpointChart = null;
let rpmChart = null;
let blockedChart = null;
let sparklineChart = null;


/* ================= SAFE FETCH ================= */

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

    // ✅ Number() guard ensures toFixed() works even if server returns a string
    const rate = Number(data.errorRate).toFixed(2);
    const el = document.getElementById("errorRate");

    el.innerText = rate + "%";

    if (rate > 5) el.style.color = "#ef4444";
    else if (rate > 2) el.style.color = "#facc15";
    else el.style.color = "#22c55e";
}


/* ================= RATE LIMIT STATS ================= */

async function loadRateLimitStats() {
    const data = await safeFetch("/monitor/rate-limit");
    if (!data) return;

    const statusEl = document.getElementById("rateEnabled");

    statusEl.innerText = data.enabled ? "ENABLED" : "DISABLED";
    statusEl.className = data.enabled
        ? "status enabled"
        : "status disabled";

    document.getElementById("rateLimit").innerText =
        `${data.limit} requests / ${data.windowSeconds}s`;

    const blocked = data.blockedRequests || 0;
    // ✅ P0 Bug #1 FIX: removed getElementById("blockedMetric") — element does not exist in HTML
    document.getElementById("blockedRequests").innerText = blocked;

    // Calculate block percentage
    const totalRequests = await getTotalRequests();
    const blockPercentage = totalRequests > 0
        ? ((blocked / totalRequests) * 100).toFixed(1)
        : 0;

    const percentEl = document.getElementById("blockPercentage");
    if (percentEl) {
        percentEl.innerText = `${blockPercentage}% of traffic`;
    }

    // Current requests per second (estimate)
    const rpsEl = document.getElementById("currentRPS");
    if (rpsEl) {
        const rpmData = await safeFetch("/monitor/rpm");
        if (rpmData && rpmData.length > 0) {
            const lastMinute = rpmData[rpmData.length - 1];
            const rps = Math.round(lastMinute / 60);
            rpsEl.innerText = rps;
        }
    }

    // Blocked in last minute
    const blockedLastMinEl = document.getElementById("blockedLastMin");
    if (blockedLastMinEl) {
        const blockedData = await safeFetch("/monitor/blocked-trend");
        if (blockedData && blockedData.length > 0) {
            blockedLastMinEl.innerText = blockedData[blockedData.length - 1];
        }
    }
}

async function getTotalRequests() {
    const data = await safeFetch("/monitor/metrics");
    return data ? data.totalRequests : 0;
}


/* ================= SPARKLINE CHART ================= */

async function loadSparklineChart() {
    const data = await safeFetch("/monitor/blocked-trend");
    if (!data) return;

    const ctx = document.getElementById("blockedSparkline");
    if (!ctx) return;

    if (!sparklineChart) {
        sparklineChart = new Chart(ctx, {
            type: "line",
            data: {
                labels: Array.from({ length: data.length }, (_, i) => ''),
                datasets: [{
                    data: data,
                    borderColor: "#ef4444",
                    backgroundColor: "rgba(239,68,68,0.2)",
                    borderWidth: 2,
                    tension: 0.4,
                    fill: true,
                    pointRadius: 0,
                    pointHoverRadius: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: { duration: 300 },
                plugins: {
                    legend: { display: false },
                    tooltip: { enabled: false }
                },
                scales: {
                    x: { display: false },
                    y: {
                        display: false,
                        beginAtZero: true
                    }
                },
                interaction: {
                    mode: null
                }
            }
        });
    } else {
        sparklineChart.data.datasets[0].data = data;
        sparklineChart.update('none');
    }
}


/* ================= ENDPOINT P95 CHART ================= */

async function loadEndpointChart() {

    const windowSelect = document.getElementById("windowSelect");
    if (!windowSelect) return;

    const windowValue = windowSelect.value;

    const data = await safeFetch(
        `/monitor/endpoints?window=${windowValue}`
    );
    if (!data) return;

    const labels = data.map(d => d.uri);
    const values = data.map(d => d.p95Latency);

    const ctx = document.getElementById("endpointChart");
    if (!ctx) return;

    if (!endpointChart) {

        endpointChart = new Chart(ctx, {
            type: "bar",
            data: {
                labels: labels,
                datasets: [{
                    label: `P95 (${windowValue}m)`,
                    data: values,
                    backgroundColor: "#22c55e",
                    borderRadius: 6
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: { duration: 600 },
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
        });

    } else {
        endpointChart.data.labels = labels;
        endpointChart.data.datasets[0].data = values;
        endpointChart.data.datasets[0].label =
            `P95 (${windowValue}m)`;
        endpointChart.update();
    }
}


/* ================= RPM CHART ================= */

async function loadRPMChart() {

    const data = await safeFetch("/monitor/rpm");
    if (!data) return;

    const ctx = document.getElementById("rpmChart");
    if (!ctx) return;

    const labels = Array.from({ length: data.length }, (_, i) =>
        `${i - data.length + 1}m`
    );

    if (!rpmChart) {

        rpmChart = new Chart(ctx, {
            type: "line",
            data: {
                labels: labels,
                datasets: [{
                    label: "Requests Per Minute",
                    data: data,
                    borderColor: "#3b82f6",
                    backgroundColor: "rgba(59,130,246,0.2)",
                    tension: 0.4,
                    fill: true
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: { duration: 600 },
                plugins: { legend: { display: false } },
                scales: {
                    y: { beginAtZero: true }
                }
            }
        });

    } else {
        rpmChart.data.datasets[0].data = data;
        rpmChart.update();
    }
}


/* ================= BLOCKED TREND CHART ================= */

async function loadBlockedChart() {

    const data = await safeFetch("/monitor/blocked-trend");
    if (!data) return;

    const ctx = document.getElementById("blockedChart");
    if (!ctx) return;

    const labels = Array.from({ length: data.length }, (_, i) =>
        `${i - data.length + 1}m`
    );

    if (!blockedChart) {

        blockedChart = new Chart(ctx, {
            type: "line",
            data: {
                labels: labels,
                datasets: [{
                    label: "Blocked Requests",
                    data: data,
                    borderColor: "#ef4444",
                    backgroundColor: "rgba(239,68,68,0.2)",
                    tension: 0.4,
                    fill: true
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: { duration: 600 },
                plugins: { legend: { display: false } },
                scales: {
                    y: { beginAtZero: true }
                }
            }
        });

    } else {
        blockedChart.data.datasets[0].data = data;
        blockedChart.update();
    }
}


/* ================= INIT ================= */

async function loadAll() {
    await Promise.all([
        loadGlobalMetrics(),
        loadErrorRate(),
        loadRateLimitStats(),
        loadEndpointChart(),
        loadRPMChart(),
        loadBlockedChart(),
        loadSparklineChart()
    ]);
}


document.addEventListener("DOMContentLoaded", function () {

    const windowSelect =
        document.getElementById("windowSelect");

    if (windowSelect) {
        windowSelect.addEventListener(
            "change",
            loadEndpointChart
        );
    }

    loadAll();
    setInterval(loadAll, 5000);

});
