Feature: Cart receipt generation triggered by a Queue message containing BizEvent data

  Scenario: Successful PDF generation and status update for an INSERTED cart receipt
    Given a cart with id "cart-receipt-success-1" and status "INSERTED" stored into cart datastore
    And a random biz event with id "cart-receipt-success-1" enqueued on cart queue
    When the PDF receipt has been properly generate from biz event after 20000 ms
    Then the cart datastore returns the cart
    And the receipt has eventId "cart-receipt-success-1"
    And the receipt has not the status "INSERTED"
    And the receipt has the status "GENERATED"
    And the blob storage has the PDF document

  # Scenario: Successful PDF generation and status update for a RETRY cart receipt (due to previous transient error)
  #   Given a cart receipt with transaction id "cart-receipt-retry-2" and status "RETRY" stored into cart receipt datastore
  #   And a biz event message containing a biz event with transaction id "cart-receipt-retry-2" enqueued on cart receipts queue
  #   When the PDF cart receipt has been properly generate from biz event after 30000 ms
  #   Then the cart receipt datastore returns the cart receipt
  #   And the cart receipt has eventId "cart-receipt-retry-2"
  #   And the cart receipt has not the status "RETRY"
  #   And the cart receipt has status "GENERATED"
  #   And the blob storage has the generated PDF document(s)

  # Scenario: Cart receipt is discarded if status is not INSERTED or RETRY (e.g., FAILED)
  #   Given a cart receipt with transaction id "cart-receipt-discard-3" and status "FAILED" stored into cart receipt datastore
  #   And a biz event message containing a biz event with transaction id "cart-receipt-discard-3" enqueued on cart receipts queue
  #   When the function processes the queue message after 10000 ms
  #   Then the cart receipt datastore returns the cart receipt
  #   And the cart receipt has eventId "cart-receipt-discard-3"
  #   And the cart receipt has status "FAILED"
  #   And the queue message is not re-enqueued

  # Scenario: Cart receipt is discarded if the CartForReceipt is not found or invalid
  #   Given a cart receipt with transaction id "cart-receipt-discard-4" is NOT present in the datastore
  #   And a biz event message containing a biz event with transaction id "cart-receipt-discard-4" enqueued on cart receipts queue
  #   When the function processes the queue message after 10000 ms
  #   Then the cart receipt datastore does NOT contain the cart receipt with eventId "cart-receipt-discard-4"
  #   And the queue message is not re-enqueued

  # Scenario: Permanent error (e.g., PDF engine failure) causes the cart receipt status to update to FAILED after max retries
  #   Given a cart receipt with transaction id "cart-receipt-fail-5" and status "RETRY" stored into cart receipt datastore
  #   And the biz event message for transaction "cart-receipt-fail-5" has been enqueued, reaching the max retry threshold
  #   And the mock PDF Engine API is configured to return a permanent generation error
  #   When the function processes the final retry queue message after 10000 ms
  #   Then the cart receipt datastore returns the cart receipt
  #   And the cart receipt has eventId "cart-receipt-fail-5"
  #   And the cart receipt has status "FAILED"
  #   And the blob storage has NOT the generated PDF document(s)

  # Scenario: Transient error (e.g., Blob Storage failure) causes the message to be re-enqueued for retry
  #   Given a cart receipt with transaction id "cart-receipt-transient-6" and status "INSERTED" stored into cart receipt datastore
  #   And the mock Blob Storage service is configured to return a transient saving error
  #   And a biz event message containing a biz event with transaction id "cart-receipt-transient-6" enqueued on cart receipts queue
  #   When the function processes the queue message after 10000 ms
  #   Then the cart receipt datastore returns the cart receipt
  #   And the cart receipt has eventId "cart-receipt-transient-6"
  #   And the cart receipt has not the status "GENERATED"
  #   And the original biz event message is re-enqueued to the queue