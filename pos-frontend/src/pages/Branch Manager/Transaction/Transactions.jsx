import React, { useState, useEffect, useMemo } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Search,
  Download,
  DollarSign,
  ArrowUpRight,
  ArrowDownLeft,
  Printer,
} from "lucide-react";
import TransactionTable from "./TransactionTable";
import { useSelector, useDispatch } from "react-redux";
import { getOrdersByBranch } from "../../../Redux Toolkit/features/order/orderThunks";
import { useToast } from "@/components/ui/use-toast";
import { handleDownloadOrderPDF } from "../../cashier/order/pdf/pdfUtils";

export default function Transactions() {
  const { orders = [] } = useSelector((state) => state.order);
  const { branch } = useSelector((state) => state.branch);
  const dispatch = useDispatch();
  const { toast } = useToast();
  const [selectedTransaction, setSelectedTransaction] = useState(null);
  const [isViewDialogOpen, setIsViewDialogOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");

  useEffect(() => {
    if (branch?.id) {
      dispatch(getOrdersByBranch({ branchId: branch.id }));
    }
  }, [branch, dispatch]);

  const filteredTransactions = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return orders;
    return orders.filter(
      (t) =>
        String(t.id).toLowerCase().includes(q) ||
        (t.customer?.fullName || "").toLowerCase().includes(q) ||
        (t.cashier?.fullName || "").toLowerCase().includes(q) ||
        (t.paymentType || "").toLowerCase().includes(q),
    );
  }, [orders, searchQuery]);

  const totalIncome = filteredTransactions
    .filter((t) => t.totalAmount > 0)
    .reduce((sum, t) => sum + t.totalAmount, 0);

  const totalExpenses = filteredTransactions
    .filter((t) => t.totalAmount < 0)
    .reduce((sum, t) => sum + Math.abs(t.totalAmount), 0);

  const netAmount = totalIncome - totalExpenses || 0;

  const handleViewTransaction = (transaction) => {
    setSelectedTransaction(transaction);
    setIsViewDialogOpen(true);
  };

  const handleExportTransactions = () => {
    if (!filteredTransactions.length) {
      toast({
        title: "No Data to Export",
        description: "There are no transactions matching your current filter.",
        variant: "destructive",
      });
      return;
    }
    const headers = [
      "Order ID,Date,Cashier,Customer,Payment Method,Amount,Status",
    ];
    const rows = filteredTransactions.map(
      (t) =>
        `${t.id},"${t.createdAt || ""}","${t.cashier?.fullName || "N/A"}","${
          t.customer?.fullName || "Walk-in"
        }","${t.paymentType || "CASH"}",${t.totalAmount || 0},"Completed"`,
    );
    const csvContent =
      "data:text/csv;charset=utf-8," + [headers, ...rows].join("\n");
    const link = document.createElement("a");
    link.setAttribute("href", encodeURI(csvContent));
    link.setAttribute(
      "download",
      `transactions_branch_${branch?.id || "all"}.csv`,
    );
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    toast({
      title: "Transactions Exported",
      description: `Exported ${filteredTransactions.length} transaction(s) to CSV.`,
    });
  };

  const handlePrintReceipt = async () => {
    if (!selectedTransaction) return;
    await handleDownloadOrderPDF(selectedTransaction, toast);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap justify-between items-center gap-4">
        <h1 className="text-3xl font-bold tracking-tight">Transactions</h1>
        <Button
          onClick={handleExportTransactions}
          className="bg-emerald-600 hover:bg-emerald-700"
        >
          <Download className="mr-2 h-4 w-4" /> Export Transactions
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card>
          <CardContent className="p-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-500">
                  Total Income
                </p>
                <h3 className="text-2xl font-bold mt-1">
                  đ{totalIncome.toFixed(2)}
                </h3>
              </div>
              <div className="p-3 bg-green-100 rounded-full">
                <ArrowUpRight className="h-6 w-6 text-green-600" />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-500">
                  Total Expenses
                </p>
                <h3 className="text-2xl font-bold mt-1">
                  đ{totalExpenses.toFixed(2)}
                </h3>
              </div>
              <div className="p-3 bg-red-100 rounded-full">
                <ArrowDownLeft className="h-6 w-6 text-red-600" />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-500">Net Amount</p>
                <h3 className="text-2xl font-bold mt-1">
                  đ{netAmount.toFixed(2)}
                </h3>
              </div>
              <div className="p-3 bg-blue-100 rounded-full">
                <span className="h-6 w-6 text-blue-600 font-bold flex items-center justify-center text-lg">
                  VNĐ
                </span>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Interactive Search Filter */}
      <div className="relative max-w-md">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
        <Input
          placeholder="Search by Order ID, customer, cashier, or payment type..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="pl-9"
        />
      </div>

      <TransactionTable
        filteredTransactions={filteredTransactions}
        handleViewTransaction={handleViewTransaction}
      />

      <Dialog open={isViewDialogOpen} onOpenChange={setIsViewDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              Transaction Details - #ORD-{selectedTransaction?.id}
            </DialogTitle>
          </DialogHeader>
          {selectedTransaction && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-sm font-medium text-gray-500">Cashier</p>
                  <p>{selectedTransaction.cashier?.fullName || "Unknown"}</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-gray-500">
                    Date & Time
                  </p>
                  <p>{selectedTransaction.createdAt}</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-gray-500">Type</p>
                  <p>
                    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                      Sale
                    </span>
                  </p>
                </div>
                <div>
                  <p className="text-sm font-medium text-gray-500">Status</p>
                  <p>
                    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                      {selectedTransaction.status || "Completed"}
                    </span>
                  </p>
                </div>
                <div>
                  <p className="text-sm font-medium text-gray-500">
                    Payment Method
                  </p>
                  <p>{selectedTransaction.paymentType}</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-gray-500">Amount</p>
                  <p
                    className={`font-bold ${
                      selectedTransaction.totalAmount > 0
                        ? "text-green-600"
                        : "text-red-600"
                    }`}
                  >
                    đ{Math.abs(selectedTransaction.totalAmount || 0).toFixed(2)}
                  </p>
                </div>
              </div>

              <div className="pt-4 border-t">
                <h4 className="font-medium mb-2">Sale Details</h4>
                <p className="text-sm text-gray-600">
                  Invoice: INV-{selectedTransaction.id}
                </p>
                <p className="text-sm text-gray-600">
                  Customer:{" "}
                  {selectedTransaction.customer?.fullName || "Walk-in Customer"}
                </p>
                <p className="text-sm text-gray-600">
                  Items: {selectedTransaction.items?.length || 0}
                </p>
              </div>

              <div className="flex justify-end gap-2 pt-4 border-t">
                <Button variant="outline" onClick={handlePrintReceipt}>
                  <Printer className="h-4 w-4 mr-1" /> Print / Download Receipt
                </Button>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
