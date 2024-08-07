Feature: All about payment events consumed by Azure functions receipt-pdf-generator

  Scenario: a biz event enqueued on receipts queue trigger the PDF receipt generation that is stored on receipts generator and blob storage
    Given a receipt with id "receipt-generator-int-test-id-1" and status "INSERTED" stored into receipt datastore
    And a random biz event with id "receipt-generator-int-test-id-1" enqueued on receipts queue
    When the PDF receipt has been properly generate from biz event after 20000 ms
    Then the receipts datastore returns the receipt
    And the receipt has eventId "receipt-generator-int-test-id-1"
    And the receipt has not the status "TO_REVIEW"
    And the receipt has not the status "NOT_QUEUE_SENT"
    And the receipt has not the status "INSERTED"
    And the blob storage has the PDF document

  Scenario: a biz event enqueued on receipts poison queue is enqueued on receipt queue that trigger the PDF receipt generation
    Given a receipt with id "receipt-generator-int-test-id-2" and status "INSERTED" stored into receipt datastore
    And a random biz event with id "receipt-generator-int-test-id-2" enqueued on receipts poison queue with poison retry "false"
    When the PDF receipt has been properly generate from biz event after 20000 ms
    Then the receipts datastore returns the receipt
    And the receipt has eventId "receipt-generator-int-test-id-2"
    And the receipt has not the status "TO_REVIEW"
    And the receipt has not the status "NOT_QUEUE_SENT"
    And the receipt has not the status "INSERTED"
    And the blob storage has the PDF document

  Scenario: a biz event enqueued on receipts poison queue is stored on receipt-message-error datastore and the receipt status is updated to TO_REVIEW
    Given a receipt with id "receipt-generator-int-test-id-3" and status "INSERTED" stored into receipt datastore
    And a random biz event with id "receipt-generator-int-test-id-3" enqueued on receipts poison queue with poison retry "true"
    When the biz event has been properly stored on receipt-message-error datastore after 20000 ms
    Then the receipt-message-error datastore returns the error receipt
    And the error receipt has the status "TO_REVIEW"
    And the receipts datastore returns the updated receipt
    And the receipt has the status "TO_REVIEW"

  Scenario: a biz event stored on receipt-message-error is enqueued on receipt queue that trigger the PDF receipt generation
    Given a receipt with id "receipt-generator-int-test-id-4" and status "TO_REVIEW" stored into receipt datastore
    And a error receipt with id "receipt-generator-int-test-id-4" stored into receipt-message-error datastore with status REVIEWED
    When the PDF receipt has been properly generate from biz event after 20000 ms
    Then the receipts datastore returns the receipt
    And the receipt has eventId "receipt-generator-int-test-id-4"
    And the receipt has not the status "TO_REVIEW"
    And the receipt has not the status "NOT_QUEUE_SENT"
    And the receipt has not the status "INSERTED"
    And the blob storage has the PDF document
    When the error receipt has been properly stored on receipt-message-error datastore after 0 ms
    Then the receipt-message-error datastore returns the error receipt
    And the error receipt has the status "REQUEUED"

  Scenario: a list of three biz event enqueued on receipts queue trigger the PDF receipt generation that is stored on receipts generator and blob storage
    Given a receipt with id "receipt-generator-int-test-transactionId-1" and status "INSERTED" stored into receipt datastore
    And a list of 3 biz event with id "receipt-generator-int-test-id-5" and transactionId "receipt-generator-int-test-transactionId-1" enqueued on receipts queue
    When the PDF receipt has been properly generate from biz event after 20000 ms
    Then the receipts datastore returns the receipt
    And the receipt has eventId "receipt-generator-int-test-transactionId-1"
    And the receipt has not the status "TO_REVIEW"
    And the receipt has not the status "NOT_QUEUE_SENT"
    And the receipt has not the status "INSERTED"
    And the blob storage has the PDF document

  Scenario: a list of three biz event enqueued on receipts poison queue is stored on receipt-message-error datastore and the receipt status is updated to TO_REVIEW
    Given a receipt with id "receipt-generator-int-test-transactionId-2" and status "INSERTED" stored into receipt datastore
    And a list of 3 biz event with id "receipt-generator-int-test-id-6" and transactionId "receipt-generator-int-test-transactionId-2" enqueued on receipts poison queue with poison retry "true"
    When the biz event has been properly stored on receipt-message-error datastore after 20000 ms
    Then the receipt-message-error datastore returns the error receipt
    And the error receipt has the status "TO_REVIEW"
    And the receipts datastore returns the updated receipt
    And the receipt has the status "TO_REVIEW"

  Scenario: a biz event enqueued on receipts queue trigger the PDF receipt generation that is stored on receipts generator and blob storage, with wisp noticeCode
    Given a receipt with id "receipt-generator-int-test-id-7" and status "INSERTED" stored into receipt datastore
    And a random biz event with id "receipt-generator-int-test-id-7" enqueued on receipts queue with wisp noticeCode
    When the PDF receipt has been properly generate from biz event after 20000 ms
    Then the receipts datastore returns the receipt
    And the receipt has eventId "receipt-generator-int-test-id-7"
    And the receipt has not the status "TO_REVIEW"
    And the receipt has not the status "NOT_QUEUE_SENT"
    And the receipt has not the status "INSERTED"
    And the blob storage has the PDF document

  Scenario: a biz event enqueued on receipts queue trigger the PDF receipt generation that is stored on receipts generator and blob storage, with wisp noticeCode and missing iuv
    Given a receipt with id "receipt-generator-int-test-id-8" and status "INSERTED" stored into receipt datastore
    And a random biz event with id "receipt-generator-int-test-id-8" enqueued on receipts queue with wisp noticeCode and missing iuv
    When the PDF receipt has been properly generate from biz event after 20000 ms
    Then the receipts datastore returns the receipt
    And the receipt has eventId "receipt-generator-int-test-id-8"
    And the receipt has not the status "TO_REVIEW"
    And the receipt has not the status "NOT_QUEUE_SENT"
    And the receipt has not the status "INSERTED"
    And the blob storage has the PDF document
