import { useState, useEffect, useRef } from "react";
import { useSelector } from "react-redux";
import { ShoppingBag, ShoppingCart, CreditCard } from "lucide-react";
import POSHeader from "./components/POSHeader";
import ProductSection from "./product/ProductSection";
import CartSection from "./cart/CartSection";
import CustomerPaymentSection from "./payment/CustomerPaymentSection";
import PaymentDialog from "./payment/PaymentDialog";
import ReceiptDialog from "./components/ReceiptDialog";
import HeldOrdersDialog from "./components/HeldOrdersDialog";
import CustomerDialog from "./customer/CustomerDialog";
import InvoiceDialog from "./order/OrderDetails/InvoiceDialog";
import { selectCartItems } from "../../Redux Toolkit/features/cart/cartSlice";

const CreateOrderPage = () => {
  const [showCustomerDialog, setShowCustomerDialog] = useState(false);
  const [showPaymentDialog, setShowPaymentDialog] = useState(false);
  const [showReceiptDialog, setShowReceiptDialog] = useState(false);
  const [showHeldOrdersDialog, setShowHeldOrdersDialog] = useState(false);
  const [activeMobileTab, setActiveMobileTab] = useState("products");

  const searchInputRef = useRef(null);
  const discountInputRef = useRef(null);
  const cartItems = useSelector(selectCartItems);

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === "F1") {
        e.preventDefault();
        setActiveMobileTab("products");
        searchInputRef.current?.focus();
      } else if (e.key === "F2") {
        e.preventDefault();
        setActiveMobileTab("payment");
        discountInputRef.current?.focus();
      } else if (e.key === "F3" || e.key === "F4") {
        e.preventDefault();
        setShowCustomerDialog(true);
      } else if (
        e.key === "F9" ||
        (e.ctrlKey && e.key === "Enter" && cartItems.length > 0)
      ) {
        e.preventDefault();
        if (cartItems.length > 0) {
          setShowPaymentDialog(true);
        }
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [cartItems.length]);

  return (
    <div className="h-full flex flex-col bg-background overflow-hidden">
      <POSHeader />

      <div className="flex-1 flex flex-col lg:flex-row overflow-hidden min-h-0">
        {/* Cột 1: Danh sách sản phẩm */}
        <div
          className={`${
            activeMobileTab === "products" ? "flex" : "hidden"
          } lg:flex lg:w-[44%] xl:w-[46%] h-full overflow-hidden min-w-0`}
        >
          <ProductSection searchInputRef={searchInputRef} />
        </div>

        {/* Cột 2: Giỏ hàng */}
        <div
          className={`${
            activeMobileTab === "cart" ? "flex" : "hidden"
          } lg:flex lg:w-[32%] xl:w-[32%] h-full overflow-hidden min-w-0`}
        >
          <CartSection setShowHeldOrdersDialog={setShowHeldOrdersDialog} />
        </div>

        {/* Cột 3: Khách hàng & Thanh toán */}
        <div
          className={`${
            activeMobileTab === "payment" ? "flex" : "hidden"
          } lg:flex lg:w-[24%] xl:w-[22%] lg:min-w-[260px] h-full overflow-hidden`}
        >
          <CustomerPaymentSection
            setShowCustomerDialog={setShowCustomerDialog}
            setShowPaymentDialog={setShowPaymentDialog}
            discountInputRef={discountInputRef}
          />
        </div>
      </div>

      {/* Thanh chuyển Tab cho Mobile & Tablet (< 1024px) */}
      <div className="lg:hidden grid grid-cols-3 border-t bg-card p-2 gap-2 shrink-0">
        <button
          type="button"
          onClick={() => setActiveMobileTab("products")}
          className={`flex items-center justify-center gap-1.5 py-2.5 px-2 rounded-lg text-xs sm:text-sm font-medium transition-colors ${
            activeMobileTab === "products"
              ? "bg-primary text-primary-foreground shadow-xs"
              : "text-muted-foreground hover:bg-muted"
          }`}
        >
          <ShoppingBag className="w-4 h-4 shrink-0" />
          <span className="truncate">Products</span>
        </button>
        <button
          type="button"
          onClick={() => setActiveMobileTab("cart")}
          className={`flex items-center justify-center gap-1.5 py-2.5 px-2 rounded-lg text-xs sm:text-sm font-medium transition-colors ${
            activeMobileTab === "cart"
              ? "bg-primary text-primary-foreground shadow-xs"
              : "text-muted-foreground hover:bg-muted"
          }`}
        >
          <ShoppingCart className="w-4 h-4 shrink-0" />
          <span className="truncate">Cart ({cartItems.length})</span>
        </button>
        <button
          type="button"
          onClick={() => setActiveMobileTab("payment")}
          className={`flex items-center justify-center gap-1.5 py-2.5 px-2 rounded-lg text-xs sm:text-sm font-medium transition-colors ${
            activeMobileTab === "payment"
              ? "bg-primary text-primary-foreground shadow-xs"
              : "text-muted-foreground hover:bg-muted"
          }`}
        >
          <CreditCard className="w-4 h-4 shrink-0" />
          <span className="truncate">Checkout</span>
        </button>
      </div>

      <CustomerDialog
        showCustomerDialog={showCustomerDialog}
        setShowCustomerDialog={setShowCustomerDialog}
      />

      <PaymentDialog
        showPaymentDialog={showPaymentDialog}
        setShowPaymentDialog={setShowPaymentDialog}
        setShowReceiptDialog={setShowReceiptDialog}
      />

      <InvoiceDialog
        showInvoiceDialog={showReceiptDialog}
        setShowInvoiceDialog={setShowReceiptDialog}
      />

      <HeldOrdersDialog
        showHeldOrdersDialog={showHeldOrdersDialog}
        setShowHeldOrdersDialog={setShowHeldOrdersDialog}
      />
    </div>
  );
};

export default CreateOrderPage;
