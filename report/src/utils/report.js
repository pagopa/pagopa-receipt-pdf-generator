const fs = require("fs");
const {
  getReceiptsStatusCount,
  getCartReceiptsStatusCount,
  getBizCount,
} = require("./utils");

const dateRange = process.env.DATE_RANGE || "weekly";

const currentDate = new Date();

const customStartDate = process.env.CUSTOM_START_DATE || currentDate.getDate();
const customEndDate = process.env.CUSTOM_END_DATE || currentDate.getDate();

let yesterday = new Date(currentDate);
yesterday.setDate(yesterday.getDate() - 1);

let minDate = new Date(currentDate);

switch (dateRange) {
  case "daily":
    minDate.setDate(minDate.getDate() - 1);
    break;
  case "weekly":
    minDate.setDate(minDate.getDate() - 7);
    break;
  case "dozen":
    minDate.setDate(minDate.getDate() - 12);
    break;
  case "monthly":
    minDate.setDate(minDate.getDate() - 30);
    break;
  case "custom":
    minDate.setDate(customStartDate);
    yesterday.setDate(customEndDate);
    break;
}

function pad2(n) {
  return n.toString().padStart(2, "0");
}

function formatDate(date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(
    date.getDate()
  )}`;
}

const minDate_ = formatDate(minDate);
const yesterday_ = formatDate(yesterday);

const from = `${minDate_}T00:00:00`;
const to = `${yesterday_}T23:59:59`;

const STATUS_GROUPS_SINGLE = {
  notified: ["IO_NOTIFIED"],
  notNotified: ["NOT_TO_NOTIFY"],
  pending: ["GENERATED", "INSERTED", "RETRY", "IO_NOTIFIER_RETRY"],
  error: [
    "NOT_QUEUE_SENT",
    "FAILED",
    "IO_ERROR_TO_NOTIFY",
    "UNABLE_TO_SEND",
    "TO_REVIEW",
  ],
};

const STATUS_GROUPS_CART = {
  ...STATUS_GROUPS_SINGLE,
  pending: [...STATUS_GROUPS_SINGLE.pending, "WAITING_FOR_BIZ_EVENT"],
};

/* =======================
 * Utility
 * ======================= */

function toMap(resources = []) {
  return resources.reduce((acc, r) => {
    acc[r.status] = r.num;
    return acc;
  }, {});
}

function sumStatuses(map, statuses) {
  return statuses.reduce((tot, s) => tot + (map[s] || 0), 0);
}

function line(icon, label, value, total) {
  const perc = total > 0 ? ((100 * value) / total).toFixed(2) : "0.00";
  return `${icon} ${label}: *${perc}%* - \`${value.toLocaleString("it-IT")}\`\n`;
}

function buildSection(title, map, groups) {
  const total = Object.values(map).reduce((a, b) => a + b, 0);

  let text = `*\n${title}*\n`;
  text += line(
    "🟢",
    "Ricevute inviate su IO",
    sumStatuses(map, groups.notified),
    total
  );
  text += line(
    "⚪️",
    "Ricevute di debitori/pagatori non presenti su IO",
    sumStatuses(map, groups.notNotified),
    total
  );
  text += line(
    "🟡",
    "Ricevute in attesa di essere inviate",
    sumStatuses(map, groups.pending),
    total
  );
  text += line(
    "🔴",
    "Ricevute non inviate a causa di errore",
    sumStatuses(map, groups.error),
    total
  );

  return { text, total };
}

/* =======================
 * Main
 * ======================= */

async function start() {
  const [bizRes, singleRes, cartRes] = await Promise.all([
    getBizCount(from, to),
    getReceiptsStatusCount(from, to),
    getCartReceiptsStatusCount(from, to),
  ]);

  const totBiz = bizRes.resources[0]?.num || 0;

  const singleMap = toMap(singleRes.resources);
  const cartMap = toMap(cartRes.resources);

  const singleSection = buildSection(
    "📄 Ricevute singole",
    singleMap,
    STATUS_GROUPS_SINGLE
  );

  const cartSection = buildSection(
    "🛒 Ricevute carrello",
    cartMap,
    STATUS_GROUPS_CART
  );

  const report = {
    text:
      `📈 _Riepilogo dal_ *${minDate_}* _al_ *${yesterday_}*\n` +
      `-\n` +
      `Pagamenti registrati sul nodo 🪢 \`${totBiz.toLocaleString(
        "it-IT"
      )}\` (di cui \`${singleSection.total.toLocaleString(
        "it-IT"
      )}\` con CF debitore e/o pagatore noto)\n` +
      `-\n` +
      singleSection.text +
      `Totale ricevute singole: \`${singleSection.total.toLocaleString(
        "it-IT"
      )}\`\n` +
      `-\n` +
      cartSection.text +
      `Totale ricevute carrello: \`${cartSection.total.toLocaleString(
        "it-IT"
      )}\`\n`,
  };

  fs.writeFileSync("report.json", JSON.stringify(report, null, 2));
  console.log(report);
}

start();