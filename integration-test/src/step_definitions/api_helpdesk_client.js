const axios = require("axios");

// --- INSTANCE CONFIGURATION ---
const helpdeskClient = axios.create({
    baseURL: process.env.HELPDESK_URL,
    headers: {
        'Ocp-Apim-Subscription-Key': (process.env.HELPDESK_SUBKEY || "").trim(),
    }
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