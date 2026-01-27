const axios = require("axios");

// --- INSTANCE CONFIGURATION ---
const helpdeskClient = axios.create({
    baseURL: process.env.HELPDESK_URL,
    headers: {
        'Ocp-Apim-Subscription-Key': (process.env.HELPDESK_SUBKEY || "").trim(),
    }
});

helpdeskClient.interceptors.request.use(config => {
    const subKey = config.headers['Ocp-Apim-Subscription-Key'] || "";
    const canary = config.headers['X-CANARY'] || "not set";

    console.log(`[Axios Request] URL: ${config.baseURL || ''}${config.url || ''}`);
    console.log(`[Auth Check] SubKey Length: ${String(subKey).length}`);
    console.log(`[Canary Check] Header: ${canary}`);

    if (subKey && (subKey.startsWith(' ') || subKey.endsWith(' '))) {
        console.warn("WARNING: The key contains spaces at the beginning or end!");
    }

    return config;
});

// Conditional management of Canary
if (process.env.canary) {
    helpdeskClient.defaults.headers.common['X-CANARY'] = 'canary';
}

// --- GENERIC HELPER FOR POSTS ---
async function performPost(path) {
    console.log("URL:", process.env.HELPDESK_URL);
    console.log("SubKey length:", (process.env.HELPDESK_SUBKEY || "").length);
    try {
        return await helpdeskClient.post(path, {});
    } catch (error) {
        return error.response;
    }
}

// --- API FUNCTIONS ---

async function postRegenerateReceiptPdf(eventId) {
    const endpoint = "receipts/{bizevent-id}/regenerate-receipt-pdf"
        .replace("{bizevent-id}", eventId);

    return await performPost(endpoint);
}

async function postRegenerateCartReceiptPdf(cartId) {
    const endpoint = "cart-receipts/{cart-id}/regenerate-receipt-pdf"
        .replace("{cart-id}", cartId);

    return await performPost(endpoint);
}

module.exports = {
    postRegenerateReceiptPdf,
    postRegenerateCartReceiptPdf
};