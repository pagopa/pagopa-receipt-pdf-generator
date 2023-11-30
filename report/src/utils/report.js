const { getReceiptsStatusCount } = require("./utils");

// const from = "2023-11-23T00:00:00";
// const to = "2023-11-25T23:59:59";

let currentDate = new Date()
let yesterday = new Date(currentDate)
yesterday.setDate(yesterday.getDate() - 1)
// console.log(yesterday);

function padTo2Digits(num) {
    return num.toString().padStart(2, '0');
  }

// function formatDate(date) {
// return (
//     [
//     date.getFullYear(),
//     padTo2Digits(date.getMonth() + 1),
//     padTo2Digits(date.getDate()),
//     ].join('-') +
//     'T' +
//     [
//     padTo2Digits(date.getHours()),
//     padTo2Digits(date.getMinutes()),
//     padTo2Digits(date.getSeconds()),
//     ].join(':')
// );
// }
function formatDate(date) {
return (
    [
    date.getFullYear(),
    padTo2Digits(date.getMonth() + 1),
    padTo2Digits(date.getDate()),
    ].join('-')
);
}

// console.log(formatDate(new Date()));
yesterday_=formatDate(yesterday);
// console.log(formatDate(yesterday));


const res = getReceiptsStatusCount(yesterday_+"T00:00:00",yesterday_+"T23:59:59");

const dictionary = {
    "NOT_QUEUE_SENT" : "🟢",
    "INSERTED" : "🟡",
    "RETRY" : "🟡",
    "GENERATED" : "🟡",
    "SIGNED" : "🟡",
    "FAILED" : "🔴",
    "IO_NOTIFIED" : "🟢",
    "IO_ERROR_TO_NOTIFY" :  "🔴",
    "IO_NOTIFIER_RETRY" : "🟡",
    "UNABLE_TO_SEND" : "🔴",
    "NOT_TO_NOTIFY" : "🟢"
  }

console.log(`> Report 📈 receipt 🧾 of ${yesterday_} 🧐`);
res.then(function(result) {
    console.log(result.resources.forEach(e => {
        console.log(`> ${dictionary[e.status]} ${e.num.toString().padEnd(8, ' ')}\t ${e.status} `);
    }))
 })
