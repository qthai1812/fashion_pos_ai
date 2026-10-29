import React, { useState, useMemo } from "react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { SearchIcon } from "lucide-react";
import { formatDate } from "../../order/data";
import { useSelector } from "react-redux";

const OrderTable = ({ handleSelectOrder }) => {
  const { orders = [], loading, error } = useSelector((state) => state.order);
  const { refundsByBranch = [] } = useSelector((state) => state.refund);
  const [searchQuery, setSearchQuery] = useState("");

  const refundedOrderIds = new Set(
    (refundsByBranch || []).map((r) => r.orderId),
  );

  const filteredOrders = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return orders;
    return orders.filter(
      (order) =>
        String(order.id).toLowerCase().includes(q) ||
        (order.customer?.fullName || "").toLowerCase().includes(q) ||
        (order.paymentType || "").toLowerCase().includes(q),
    );
  }, [orders, searchQuery]);

  return (
    <div className="w-full p-4 flex flex-col">
      <div className="mb-4 max-w-md relative">
        <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
        <Input
          type="text"
          placeholder="Search by Order ID, customer name, or payment mode..."
          className="pl-9"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
        />
      </div>

      <div className="flex-1 overflow-auto">
        {loading ? (
          <div className="flex flex-col items-center justify-center h-full text-center text-muted-foreground">
            <span>Loading orders...</span>
          </div>
        ) : error ? (
          <div className="flex flex-col items-center justify-center h-full text-center text-destructive">
            <span>{error}</span>
          </div>
        ) : filteredOrders.length > 0 ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Order ID</TableHead>
                <TableHead>Date/Time</TableHead>
                <TableHead>Customer</TableHead>
                <TableHead>Amount</TableHead>
                <TableHead>Payment Mode</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredOrders.map((order) => (
                <TableRow key={order.id}>
                  <TableCell className="font-medium">#{order.id}</TableCell>
                  <TableCell>{formatDate(order.createdAt)}</TableCell>
                  <TableCell>
                    {order.customer?.fullName || "Walk-in Customer"}
                  </TableCell>
                  <TableCell>đ{order.totalAmount?.toFixed(2)}</TableCell>
                  <TableCell>{order.paymentType}</TableCell>
                  <TableCell className="text-right">
                    {refundedOrderIds.has(order.id) ? (
                      <Button disabled variant="secondary">
                        Refunded
                      </Button>
                    ) : (
                      <Button onClick={() => handleSelectOrder(order)}>
                        Select for Return
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <div className="flex flex-col items-center justify-center h-full text-center text-muted-foreground">
            <SearchIcon size={48} strokeWidth={1} />
            <p className="mt-4">No orders found</p>
            <p className="text-sm">
              Try searching by order ID or customer name
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default OrderTable;
