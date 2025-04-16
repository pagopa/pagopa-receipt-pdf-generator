const fs = require('fs');

const { getReceiptsStatusCount, getBizCount } = require("./utils");

// const from = "2023-11-23T00:00:00";
// const to = "2023-11-25T23:59:59";

const dateRange = process.env.DATE_RANGE || 'weekly'; // set daily for today

let currentDate = new Date();

const customStartDate = process.env.CUSTOM_START_DATE || currentDate.getDate();
const customEndDate = process.env.CUSTOM_END_DATE || currentDate.getDate();

let yesterday = new Date(currentDate);
yesterday.setDate(yesterday.getDate() - 1); // remove -1 for today

let minDate = new Date(currentDate);
switch(dateRange) {
    case "daily":
        minDate.setDate(minDate.getDate() - 1); // remove -1 for today
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
}

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
minDate_ = formatDate(minDate);
yesterday_ = formatDate(yesterday)
// console.log(formatDate(yesterday));


// Start function
const start = async function (a, b) {
  const resBiz = await getBizCount(minDate_ + "T00:00:00", yesterday_ + "T23:59:59");
  const totBiz = resBiz.resources[0].num;
  // console.log(totBiz);

  // >>>>>>>>>>>>> start-RECEIPTs
  const res = getReceiptsStatusCount(minDate_ + "T00:00:00", yesterday_ + "T23:59:59");

  const dictionary = {
    "NOT_QUEUE_SENT": "🟢",
    "INSERTED": "🟡",
    "RETRY": "🟡",
    "GENERATED": "🟡",
    "SIGNED": "🟡",
    "FAILED": "🔴",
    "IO_NOTIFIED": "🟢",
    "IO_ERROR_TO_NOTIFY": "🔴",
    "IO_NOTIFIER_RETRY": "🟡",
    "UNABLE_TO_SEND": "🔴",
    "NOT_TO_NOTIFY": "🟢",
    "TO_REVIEW": "🔴"
  }


  // let report_ = '{"title":"","detail":[]}'
  let report_ = '{"text":""}'

  report = JSON.parse(report_);

  report.text = `📈 _Riepilogo dal_ *${minDate_}* _al_ *${yesterday_}*\n`
  let p = res.then(function (result) {
  //   console.log(result.resources.forEach(e => {
  //       console.log(`> ${dictionary[e.status]} ${e.num.toString().padEnd(8, ' ')}\t ${e.status} `);
  //   }))
    let sum = 0;
    let dic_sum = {}
    result.resources.forEach(element => {
      console.log(`${element.status} : ${element.num}`)
      sum += element.num;
      dic_sum[element.status] = element.num;
    });
    // console.log(dic_sum);

    report.text += `-\n`;
    report.text += `Pagamenti registrati sul nodo 🪢 \`${totBiz.toLocaleString('it-IT')}\` (di cui \`${sum?.toLocaleString('it-IT')}\` con CF debitore e/o pagatore noto)\n`;
    report.text += `-\n`;

    // :large_green_circle: Ricevute inviate su IO: YY% · numeroAssolutoB
    let notified = (dic_sum["IO_NOTIFIED"] != undefined ? dic_sum["IO_NOTIFIED"] : 0);
    report.text += `🟢 Ricevute inviate su IO: *${(100 * notified / sum).toFixed(2)}%* - \`${notified?.toLocaleString('it-IT')}\` \n`;

    // :white_circle: Ricevute di debitori non presenti su IO: ZZ% · numeroAssolutoC
    let notNotified = (dic_sum["NOT_TO_NOTIFY"] != undefined ? dic_sum["NOT_TO_NOTIFY"] : 0);
    report.text += `⚪️ Ricevute di debitori/pagatori non presenti su IO : *${(100 * notNotified / sum).toFixed(2)}%* - \`${notNotified?.toLocaleString('it-IT')}\` \n`;

    // :large_yellow_circle: Ricevute in attesa di essere inviate: QQ% · numeroAssolutoD
    let GENERATED_INSERTED = (dic_sum["GENERATED"] != undefined ? dic_sum["GENERATED"] : 0) + (dic_sum["INSERTED"] != undefined ? dic_sum["INSERTED"] : 0);
    report.text += `🟡 Ricevute in attesa di essere inviate: *${(100 * GENERATED_INSERTED / sum).toFixed(2)}%* - \`${GENERATED_INSERTED?.toLocaleString('it-IT')}\` \n`;

    // :red_circle: Ricevute non inviate a causa di un errore: NN% · numeroAssolutoE (edited)
    let errori = (dic_sum["NOT_QUEUE_SENT"] != undefined ? dic_sum["NOT_QUEUE_SENT"] : 0) +
      (dic_sum["FAILED"] != undefined ? dic_sum["FAILED"] : 0) +
      (dic_sum["IO_ERROR_TO_NOTIFY"] != undefined ? dic_sum["IO_ERROR_TO_NOTIFY"] : 0) +
      (dic_sum["UNABLE_TO_SEND"] != undefined ? dic_sum["UNABLE_TO_SEND"] : 0) +
      (dic_sum["TO_REVIEW"] != undefined ? dic_sum["TO_REVIEW"] : 0);
    report.text += `🔴 Ricevute non inviate a causa di un errore: *${(100 * errori / sum).toFixed(2)}%* - \`${errori?.toLocaleString('it-IT')}\` \n`;


    // result.resources.forEach(e => {
    //   // report["detail"].push( {"status": `${dictionary[e.status]} ${e.status}`, "num":`${e.num}` });
    //   report.text+=`${dictionary[e.status]} ${e.num.toString().padEnd(15, ' ')}\t ${e.status} \n`
    // })

    console.log(report);
    // console.log(JSON.stringify(report));
    fs.writeFileSync('report.json', JSON.stringify(report));


  });
  // >>>>>>>>>>>>> stop-RECEIPTs

};

start();



