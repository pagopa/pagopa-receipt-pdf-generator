package it.gov.pagopa.receipt.pdf.generator.service.impl;

import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.exception.TemplateDataMappingException;
import it.gov.pagopa.receipt.pdf.generator.model.template.*;
import it.gov.pagopa.receipt.pdf.generator.service.BuildTemplateService;
import it.gov.pagopa.receipt.pdf.generator.utils.BizEventToPdfMapper;

import java.util.Collections;

public class BuildTemplateServiceImpl implements BuildTemplateService {

    /**
     * {@inheritDoc}
     */
    @Override
    public ReceiptPDFTemplate buildTemplate(BizEvent bizEvent, boolean partialTemplate) throws TemplateDataMappingException {
        return ReceiptPDFTemplate.builder()
                .transaction(Transaction.builder()
                        .id(BizEventToPdfMapper.getId(bizEvent))
                        .timestamp(BizEventToPdfMapper.getTimestamp(bizEvent))
                        .amount(BizEventToPdfMapper.getAmount(bizEvent))
                        .psp(BizEventToPdfMapper.getPsp(bizEvent))
                        .rrn(BizEventToPdfMapper.getRnn(bizEvent))
                        .paymentMethod(PaymentMethod.builder()
                                .name(BizEventToPdfMapper.getPaymentMethodName(bizEvent))
                                .logo(BizEventToPdfMapper.getPaymentMethodLogo(bizEvent))
                                .accountHolder(BizEventToPdfMapper.getPaymentMethodAccountHolder(bizEvent))
                                .build())
                        .authCode(BizEventToPdfMapper.getAuthCode(bizEvent))
                        .requestedByDebtor(partialTemplate)
                        .build())
                .user(partialTemplate ?
                        null :
                        User.builder()
                                .data(UserData.builder()
                                        .fullName(BizEventToPdfMapper.getUserFullName(bizEvent))
                                        .taxCode(BizEventToPdfMapper.getUserTaxCode(bizEvent))
                                        .build())
                                .build())
                .cart(Cart.builder()
                        .items(Collections.singletonList(
                                Item.builder()
                                        .refNumber(RefNumber.builder()
                                                .type(BizEventToPdfMapper.getRefNumberType(bizEvent))
                                                .value(BizEventToPdfMapper.getRefNumberValue(bizEvent))
                                                .build())
                                        .debtor(Debtor.builder()
                                                .fullName(BizEventToPdfMapper.getDebtorFullName(bizEvent))
                                                .taxCode(BizEventToPdfMapper.getDebtorTaxCode(bizEvent))
                                                .build())
                                        .payee(Payee.builder()
                                                .name(BizEventToPdfMapper.getPayeeName(bizEvent))
                                                .taxCode(BizEventToPdfMapper.getPayeeTaxCode(bizEvent))
                                                .build())
                                        .subject(BizEventToPdfMapper.getItemSubject(bizEvent))
                                        .amount(BizEventToPdfMapper.getItemAmount(bizEvent))
                                        .build()
                        ))
                        //Cart items total amount w/o fee, TODO change it with multiple cart items implementation
                        .amountPartial(BizEventToPdfMapper.getItemAmount(bizEvent))
                        .build())
                .build();
    }
}
