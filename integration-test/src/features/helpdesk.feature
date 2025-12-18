Feature: Helpdesk receipt regenerate

  Scenario: regenerateReceiptPdf API retrieve the receipt with the given eventId and regenerate its pdf updating receipt's metadata
    Given a receipt with id "receipt-generator-helpdesk-int-test-id-1" and status "IO_NOTIFIED" stored into receipt datastore
    And a biz event with id "receipt-generator-helpdesk-int-test-id-1" and status "DONE" stored on biz-events datastore
    When regenerateReceiptPdf API is called with bizEventId "receipt-generator-helpdesk-int-test-id-1" as path param
    Then the api response has a 200 Http status
    And the receipt with eventId "receipt-generator-helpdesk-int-test-id-1" is recovered from datastore
    And the receipt has attachment metadata
    And the PDF is present on blob storage

  Scenario: regenerateCartReceiptPdf API retrieve the cart receipt with the given cart id and regenerate its pdf updating cart receipt's metadata
    Given a notified cart with id "receipt-generator-helpdesk-int-test-id-2" stored into cart datastore
    And random biz events for cart with id prefix "receipt-generator-helpdesk-int-test-id-2" and transaction id "receipt-generator-helpdesk-int-test-id-2" stored into biz event datastore
    When regenerateCartReceiptPdf API is called with cartId "receipt-generator-helpdesk-int-test-id-2" as path param
    Then the api response has a 200 Http status
    And the cart receipt with eventId "receipt-generator-helpdesk-int-test-id-2" is recovered from datastore
    And the cart receipt has attachment metadata
    And all the PDF are present on blob storage
