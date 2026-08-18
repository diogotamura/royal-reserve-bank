const API_BASE = "http://localhost:8080";

const elements = {
    token: document.querySelector("#token"),
    feedback: document.querySelector("#feedback"),
    accountForm: document.querySelector("#account-form"),
    accountHolder: document.querySelector("#account-holder"),
    accountBalance: document.querySelector("#account-balance"),
    accountCurrency: document.querySelector("#account-currency"),
    transactionForm: document.querySelector("#transaction-form"),
    assetCode: document.querySelector("#asset-code"),
    assetName: document.querySelector("#asset-name"),
    assetValue: document.querySelector("#asset-value"),
    transactionResult: document.querySelector("#transaction-result"),
    refreshAccounts: document.querySelector("#refresh-accounts"),
    accountsTable: document.querySelector("#accounts-table")
};

function headers() {
    const requestHeaders = {"Content-Type": "application/json"};
    const token = elements.token.value.trim();
    if (token) {
        requestHeaders.Authorization = `Bearer ${token}`;
    }
    return requestHeaders;
}

function showFeedback(message, type) {
    elements.feedback.textContent = message;
    elements.feedback.className = `feedback visible ${type}`;
}

async function request(path, options = {}) {
    const response = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers: {...headers(), ...(options.headers || {})}
    });
    const text = await response.text();
    let body = text;
    try {
        body = text ? JSON.parse(text) : "";
    } catch (ignored) {
        // The API returns plain text for account and transaction operations.
    }
    if (!response.ok) {
        const message = typeof body === "string" ? body : JSON.stringify(body);
        throw new Error(`${response.status} ${response.statusText}${message ? `: ${message}` : ""}`);
    }
    return body;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function renderAccounts(accounts) {
    if (!Array.isArray(accounts) || accounts.length === 0) {
        elements.accountsTable.innerHTML = '<tr><td colspan="4" class="empty-state">No accounts found.</td></tr>';
        return;
    }
    elements.accountsTable.innerHTML = accounts.map(account => `
        <tr>
            <td>${escapeHtml(account.accountHolderName)}</td>
            <td>${escapeHtml(account.accountNumber)}</td>
            <td>${escapeHtml(account.balance)}</td>
            <td>${escapeHtml(account.currency)}</td>
        </tr>
    `).join("");
}

async function loadAccounts(showMessage = false) {
    elements.refreshAccounts.disabled = true;
    try {
        const accounts = await request("/api/account");
        renderAccounts(accounts);
        if (showMessage) {
            showFeedback("Account list refreshed successfully.", "success");
        }
    } catch (error) {
        showFeedback(`Could not load accounts. ${error.message}`, "error");
    } finally {
        elements.refreshAccounts.disabled = false;
    }
}

elements.accountForm.addEventListener("submit", async event => {
    event.preventDefault();
    const button = event.submitter;
    button.disabled = true;
    try {
        const body = {
            accountHolderName: elements.accountHolder.value.trim(),
            balance: Number(elements.accountBalance.value),
            currency: elements.accountCurrency.value
        };
        const message = await request("/api/account", {
            method: "POST",
            body: JSON.stringify(body)
        });
        showFeedback(message || "Account opened successfully.", "success");
        await loadAccounts();
    } catch (error) {
        showFeedback(`Account could not be opened. ${error.message}`, "error");
    } finally {
        button.disabled = false;
    }
});

elements.transactionForm.addEventListener("submit", async event => {
    event.preventDefault();
    const button = event.submitter;
    button.disabled = true;
    elements.transactionResult.hidden = true;
    try {
        const body = {
            transactionItemsDtoList: [{
                assetCode: elements.assetCode.value.trim(),
                assetName: elements.assetName.value.trim(),
                value: Number(elements.assetValue.value)
            }]
        };
        const message = await request("/api/transaction", {
            method: "POST",
            body: JSON.stringify(body)
        });
        const isFallback = String(message).toLowerCase().includes("oops");
        elements.transactionResult.textContent = message;
        elements.transactionResult.className = `result-box${isFallback ? " fallback" : ""}`;
        elements.transactionResult.hidden = false;
        showFeedback(isFallback ? "The circuit-breaker fallback responded." : "Transaction completed successfully.", isFallback ? "error" : "success");
    } catch (error) {
        elements.transactionResult.textContent = error.message;
        elements.transactionResult.className = "result-box fallback";
        elements.transactionResult.hidden = false;
        showFeedback(`Transaction request failed. ${error.message}`, "error");
    } finally {
        button.disabled = false;
    }
});

elements.refreshAccounts.addEventListener("click", () => loadAccounts(true));

loadAccounts();
