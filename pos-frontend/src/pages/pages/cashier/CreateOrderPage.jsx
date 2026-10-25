import { useState, useEffect, useRef } from "react";
import { useSelector } from "react-redux";
import { useToast } from "@/components/ui/use-toast";
import { Button } from "@/components/ui/button";
import { ShoppingBag, ShoppingCart, CreditCard } from "lucide-react";

// Import components
import POSHeader from "./components/POSHeader";
import ProductSection from "./product/ProductSection";
import CartSection from "./cart/CartSection";
import CustomerPaymentSection from "./payment/CustomerPaymentSection";

import PaymentDialog from "./payment/PaymentDialog";
import HeldOrdersDialog from "./components/HeldOrdersDialog";
import CustomerDialog from "./customer/CustomerDialog";
import InvoiceDialog from "./order/OrderDetails/InvoiceDialog";
import { selectCartItems } from "../../Redux Toolkit/features/cart/cartSlice";

const CreateOrderPage = () => {
  const { toast } = useToast();
  const searchInputRef = useRef(null);

  const { error: orderError } = useSelector((state) => state.order);
  const cartItems = useSelector(selectCartItems);

  const [showCustomerDialog, setShowCustomerDialog] = useState(false);
  const [showPaymentDialog, setShowPaymentDialog] = useState(false);
  const [showReceiptDialog, setShowReceiptDialog] = useState(false);
  const [showHeldOrdersDialog, setShowHeldOrdersDialog] = useState(false);
  const [activeMobileTab, setActiveMobileTab] = useState("products");

  useEffect(() => {
    if (orderError) {
      toast({
        title: "Order Error",
        description: orderError,
        variant: "destructive",
      });
    }
  }, [orderError, toast]);

  // Focus on search input & register POS keyboard shortcuts (F1/F2, F4, F9)
  useEffect(() => {
    if (searchInputRef.current) {
      searchInputRef.current.focus();
    }

    const handleKeyDown = (e) => {
      if (e.key === "F1" || e.key === "F2") {
        e.preventDefault();
        setActiveMobileTab("products");
        searchInputRef.current?.focus();
      } else if (e.key === "F4") {
        e.preventDefault();
        setShowCustomerDialog(true);
      } else if (e.key === "F9") {
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
    <div className="h-full flex flex-col bg-background">
      {/* Header */}
      <POSHeader />

      {/* Main Content - Desktop 3-Column & Mobile/Tablet Adaptive Tab Layout */}
      <div className="flex-1 flex flex-col lg:flex-row overflow-hidden">
        {/* Left Column - Product Search & List */}
        <div
          className={`${
            activeMobileTab === "products" ? "flex" : "hidden"
          } lg:flex w-full lg:w-2/5 h-full overflow-hidden`}
        >
          <ProductSection searchInputRef={searchInputRef} />
        </div>

        {/* Middle Column - Cart */}
        <div
          className={`${
            activeMobileTab === "cart" ? "flex" : "hidden"
          } lg:flex w-full lg:w-2/5 h-full overflow-hidden`}
        >
          <CartSection setShowHeldOrdersDialog={setShowHeldOrdersDialog} />
        </div>

        {/* Right Column - Customer & Payment */}
        <div
          className={`${
            activeMobileTab === "payment" ? "flex" : "hidden"
          } lg:flex w-full lg:w-1/5 h-full overflow-hidden`}
        >
          <CustomerPaymentSection
            setShowCustomerDialog={setShowCustomerDialog}
            setShowPaymentDialog={setShowPaymentDialog}
          />
        </div>
      </div>

      {/* Mobile & Tablet Bottom Tab Bar (< 1024px) */}
      <div className="flex lg:hidden border-t bg-card p-2 gap-2">
        <Button
          variant={activeMobileTab === "products" ? "default" : "outline"}
          size="sm"
          className="flex-1"
          onClick={() => setActiveMobileTab("products")}
        >
          <ShoppingBag className="w-4 h-4 mr-1" />
          Products
        </Button>
        <Button
          variant={activeMobileTab === "cart" ? "default" : "outline"}
          size="sm"
          className="flex-1"
          onClick={() => setActiveMobileTab("cart")}
        >
          <ShoppingCart className="w-4 h-4 mr-1" />
          Cart ({cartItems.length})
        </Button>
        <Button
          variant={activeMobileTab === "payment" ? "default" : "outline"}
          size="sm"
          className="flex-1"
          onClick={() => setActiveMobileTab("payment")}
        >
          <CreditCard className="w-4 h-4 mr-1" />
          Checkout
        </Button>
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
