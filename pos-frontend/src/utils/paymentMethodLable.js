export const getPaymentMethodLabel = (method) => {
  switch (method?.toLowerCase()) {
    case "cash":
      return "Cash";
    case "card":
      return "Card";
    case "upi":
      return "UPI";
    default:
      return method;
  }
};
