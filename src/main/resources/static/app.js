const $ = (id) => document.getElementById(id);

let accessToken = "";
let replayLastPage = 0;

function baseUrl() {
  return $("baseUrl").value.trim().replace(/\/$/, "");
}

function parseBody(bodyText) {
  if (!bodyText) return {};
  try {
    return JSON.parse(bodyText);
  } catch (_error) {
    return { message: bodyText };
  }
}

function getErrorMessage(error) {
  return error?.message || error?.error || error?.code || "Unknown error";
}

function showOutput(value) {
  $("output").textContent =
    typeof value === "string" ? value : JSON.stringify(value, null, 2);
}

function showToast(message, type = "success") {
  const toast = $("toast");
  toast.textContent = message;
  toast.className = `toast ${type}`;
  setTimeout(() => {
    toast.classList.add("hidden");
  }, 2600);
}

function requireAuthHeader() {
  if (!accessToken) {
    throw new Error("JWT 토큰이 없습니다. 먼저 로그인하세요.");
  }
  return { Authorization: `Bearer ${accessToken}` };
}

async function apiGet(path, withAuth = false) {
  const headers = withAuth ? requireAuthHeader() : {};
  const response = await fetch(`${baseUrl()}${path}`, { headers });
  const body = parseBody(await response.text());
  if (!response.ok) throw body;
  return body;
}

async function apiPost(path, payload, withAuth = false) {
  const headers = { "Content-Type": "application/json" };
  if (withAuth) Object.assign(headers, requireAuthHeader());
  const response = await fetch(`${baseUrl()}${path}`, {
    method: "POST",
    headers,
    body: payload ? JSON.stringify(payload) : "{}",
  });
  const body = parseBody(await response.text());
  if (!response.ok) throw body;
  return body;
}

function fillTable(tableId, rows, cols) {
  const tbody = $(tableId).querySelector("tbody");
  tbody.innerHTML = "";
  rows.forEach((row) => {
    const tr = document.createElement("tr");
    cols.forEach((col) => {
      const td = document.createElement("td");
      td.textContent = row[col] ?? "";
      tr.appendChild(td);
    });
    tbody.appendChild(tr);
  });
}

function showConfirm(title, message) {
  return new Promise((resolve) => {
    const modal = $("confirmModal");
    const okBtn = $("confirmOkBtn");
    const cancelBtn = $("confirmCancelBtn");
    $("confirmTitle").textContent = title;
    $("confirmMessage").textContent = message;
    modal.classList.remove("hidden");

    const cleanup = (result) => {
      modal.classList.add("hidden");
      okBtn.removeEventListener("click", onOk);
      cancelBtn.removeEventListener("click", onCancel);
      resolve(result);
    };
    const onOk = () => cleanup(true);
    const onCancel = () => cleanup(false);

    okBtn.addEventListener("click", onOk);
    cancelBtn.addEventListener("click", onCancel);
  });
}

async function login() {
  try {
    const username = $("username").value.trim();
    const password = $("password").value;
    const data = await apiPost("/api/v1/auth/login", { username, password });
    accessToken = data.accessToken || "";
    $("jwtToken").value = accessToken;
    showOutput(data);
    showToast("로그인 성공", "success");
  } catch (error) {
    showOutput(error);
    showToast(`로그인 실패: ${getErrorMessage(error)}`, "error");
  }
}

function logout() {
  accessToken = "";
  $("jwtToken").value = "";
  showToast("로그아웃 완료", "success");
}

async function checkConnection() {
  try {
    await apiGet("/api/v1/interfaces");
    $("connectionStatus").value = "Connected";
    showToast("API 연결 성공", "success");
  } catch (error) {
    $("connectionStatus").value = "Failed";
    showOutput(error);
    showToast(`API 연결 실패: ${getErrorMessage(error)}`, "error");
  }
}

async function loadInterfaces() {
  try {
    const data = await apiGet("/api/v1/interfaces");
    fillTable("interfacesTable", data, [
      "interfaceCode",
      "name",
      "protocolType",
      "ownerTeam",
      "status",
    ]);
    showOutput(data);
  } catch (error) {
    showOutput(error);
    showToast(`조회 실패: ${getErrorMessage(error)}`, "error");
  }
}

async function loadDlq() {
  try {
    const ifCode = $("dlqInterfaceCode").value.trim();
    const page = $("dlqPage").value;
    const size = $("dlqSize").value;
    const query = new URLSearchParams();
    if (ifCode) query.set("interfaceCode", ifCode);
    query.set("page", page);
    query.set("size", size);
    const data = await apiGet(`/api/v1/dlq?${query.toString()}`);
    fillTable("dlqTable", data.content || [], [
      "id",
      "interfaceCode",
      "topic",
      "reason",
      "replayCount",
      "createdAt",
    ]);
    showOutput(data);
  } catch (error) {
    showOutput(error);
    showToast(`DLQ 조회 실패: ${getErrorMessage(error)}`, "error");
  }
}

async function createReplayRequest() {
  try {
    const dlqId = $("replayDlqId").value;
    const requester = $("replayRequester").value.trim();
    const reasonCode = "IF-MQ-001";
    const reasonDetail = "Requested from console";
    const payloadRaw = $("replayPayload").value.trim();
    let payloadOverride;
    if (payloadRaw) payloadOverride = JSON.parse(payloadRaw);

    const data = await apiPost(`/api/v1/dlq/${dlqId}/replay-requests`, {
      requester,
      reasonCode,
      reasonDetail,
      payloadOverride,
    });
    $("replayRequestId").value = data.id;
    showOutput(data);
    showToast("재처리 요청 생성 완료", "success");
    await loadReplayRequests();
  } catch (error) {
    showOutput(error);
    showToast(`요청 생성 실패: ${getErrorMessage(error)}`, "error");
  }
}

function replayQueryString() {
  const query = new URLSearchParams();
  const status = $("replayStatus").value;
  const fromDate = $("replayFromDate").value;
  const toDate = $("replayToDate").value;
  const page = $("replayPage").value;
  const size = $("replaySize").value;
  const sort = $("replaySort").value;
  if (status) query.set("status", status);
  if (fromDate) query.set("fromDate", fromDate);
  if (toDate) query.set("toDate", toDate);
  if (page) query.set("page", page);
  if (size) query.set("size", size);
  if (sort) query.set("sort", sort);
  return query.toString();
}

function updateReplayPageInfo(pageData) {
  const contentSize = pageData.content?.length ?? 0;
  $("replayPageInfo").value = `page ${pageData.page + 1}/${Math.max(
    pageData.totalPages || 1,
    1
  )}, items ${contentSize}/${pageData.totalElements ?? 0}`;
  replayLastPage = Math.max((pageData.totalPages || 1) - 1, 0);
}

async function loadReplayRequests() {
  try {
    const data = await apiGet(`/api/v1/dlq/replay-requests?${replayQueryString()}`);
    fillTable("replayTable", data.content || [], [
      "id",
      "dlqId",
      "interfaceCode",
      "dlqReason",
      "status",
      "requester",
      "approver",
    ]);
    updateReplayPageInfo(data);
    showOutput(data);
  } catch (error) {
    showOutput(error);
    showToast(`Replay 조회 실패: ${getErrorMessage(error)}`, "error");
  }
}

async function approveReplay() {
  try {
    const replayRequestId = $("replayRequestId").value;
    const approver = $("replayActor").value.trim();
    const ok = await showConfirm(
      "Approve Replay",
      `Replay Request #${replayRequestId} 를 승인하시겠습니까?`
    );
    if (!ok) return;

    const data = await apiPost(
      `/api/v1/dlq/replay-requests/${replayRequestId}/approve`,
      { approver },
      true
    );
    showOutput(data);
    showToast("승인 완료", "success");
    await loadReplayRequests();
  } catch (error) {
    showOutput(error);
    showToast(`승인 실패: ${getErrorMessage(error)}`, "error");
  }
}

async function rejectReplay() {
  try {
    const replayRequestId = $("replayRequestId").value;
    const approver = $("replayActor").value.trim();
    const reason = $("rejectReason").value.trim();
    const data = await apiPost(
      `/api/v1/dlq/replay-requests/${replayRequestId}/reject`,
      { approver, reason },
      true
    );
    showOutput(data);
    showToast("반려 완료", "success");
    await loadReplayRequests();
  } catch (error) {
    showOutput(error);
    showToast(`반려 실패: ${getErrorMessage(error)}`, "error");
  }
}

async function executeReplay() {
  try {
    const replayRequestId = $("replayRequestId").value;
    const executor = $("replayActor").value.trim();
    const ok = await showConfirm(
      "Execute Replay",
      `Replay Request #${replayRequestId} 를 즉시 실행하시겠습니까?`
    );
    if (!ok) return;

    const data = await apiPost(
      `/api/v1/dlq/replay-requests/${replayRequestId}/execute`,
      { executor },
      true
    );
    showOutput(data);
    showToast("재실행 완료", "success");
    await loadDlq();
    await loadReplayRequests();
  } catch (error) {
    showOutput(error);
    showToast(`재실행 실패: ${getErrorMessage(error)}`, "error");
  }
}

function prevReplayPage() {
  const page = Number($("replayPage").value || 0);
  $("replayPage").value = Math.max(page - 1, 0);
  loadReplayRequests();
}

function nextReplayPage() {
  const page = Number($("replayPage").value || 0);
  $("replayPage").value = Math.min(page + 1, replayLastPage);
  loadReplayRequests();
}

$("checkConnectionBtn").addEventListener("click", checkConnection);
$("loginBtn").addEventListener("click", login);
$("logoutBtn").addEventListener("click", logout);
$("loadInterfacesBtn").addEventListener("click", loadInterfaces);
$("loadDlqBtn").addEventListener("click", loadDlq);
$("createReplayRequestBtn").addEventListener("click", createReplayRequest);
$("loadReplayRequestsBtn").addEventListener("click", loadReplayRequests);
$("prevReplayPageBtn").addEventListener("click", prevReplayPage);
$("nextReplayPageBtn").addEventListener("click", nextReplayPage);
$("approveReplayBtn").addEventListener("click", approveReplay);
$("rejectReplayBtn").addEventListener("click", rejectReplay);
$("executeReplayBtn").addEventListener("click", executeReplay);

checkConnection();
