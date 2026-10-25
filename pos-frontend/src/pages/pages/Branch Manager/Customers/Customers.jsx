import React, { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableHeader,
  TableRow,
  TableHead,
  TableBody,
  TableCell,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import { Search, ShoppingBag, Phone, Mail, User } from "lucide-react";
import { getStatusColor } from "../../../utils/getStatusColor";
import { calculateCustomerStats } from "../../cashier/customer/utils/customerUtils";
import { useSelector, useDispatch } from "react-redux";
import { getAllCustomers } from "../../../Redux Toolkit/features/customer/customerThunks";
import { clearCustomerOrders } from "../../../Redux Toolkit/features/order/orderSlice";
import { getOrdersByCustomer } from "../../../Redux Toolkit/features/order/orderThunks";

const Customers = () => {
  const { customerOrders } = useSelector((state) => state.order);
  const { customers = [] } = useSelector((state) => state.customer);
  const [searchTerm, setSearchTerm] = useState("");
  const [isCustomerDetailsOpen, setIsCustomerDetailsOpen] = useState(false);
  const [selectedCustomer, setSelectedCustomer] = useState(null);
  const dispatch = useDispatch();

  const filteredCustomers = (customers || []).filter((customer) => {
    return (
      customer.fullName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      customer.phone?.includes(searchTerm) ||
      customer?.email?.toLowerCase().includes(searchTerm.toLowerCase())
    );
  });

  useEffect(() => {
    dispatch(getAllCustomers());
  }, [dispatch]);

  const handleSearch = (e) => {
    setSearchTerm(e.target.value);
  };

  const openCustomerDetails = (customer) => {
    setSelectedCustomer(customer);
    setIsCustomerDetailsOpen(true);
    dispatch(clearCustomerOrders());
    if (customer.id) {
      dispatch(getOrdersByCustomer(customer.id));
    }
  };

  const customerStats = selectedCustomer
    ? calculateCustomerStats(customerOrders || [])
    : null;

  const displayCustomer = selectedCustomer
    ? {
        ...selectedCustomer,
        ...customerStats,
      }
    : null;

  // Prevent division by zero (NaN) when customers array is empty or totalOrders is undefined
  const avgOrdersPerCustomer =
    customers.length > 0
      ? Math.round(
          customers.reduce((sum, c) => sum + Number(c.totalOrders || 0), 0) /
            customers.length
        )
      : 0;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-bold tracking-tight">Customer Overview</h1>
      </div>

      {/* Search */}
      <Card>
        <CardContent className="p-6">
          <div className="relative w-full max-w-sm">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              type="search"
              placeholder="Search customers..."
              className="pl-8"
              value={searchTerm}
              onChange={handleSearch}
            />
          </div>
        </CardContent>
      </Card>

      {/* Customer Stats */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <Card>
          <CardContent className="p-6">
            <div className="flex flex-col items-center justify-center">
              <h3 className="text-lg font-medium text-muted-foreground">
                Total Customers
              </h3>
              <p className="text-3xl font-bold mt-2">{customers.length}</p>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-6">
            <div className="flex flex-col items-center justify-center">
              <h3 className="text-lg font-medium text-muted-foreground">
                Gold Members
              </h3>
              <p className="text-3xl font-bold mt-2 text-primary">
                {customers.filter((c) => c.loyaltyStatus === "Gold").length}
              </p>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-6">
            <div className="flex flex-col items-center justify-center">
              <h3 className="text-lg font-medium text-muted-foreground">
                Avg. Orders per Customer
              </h3>
              <p className="text-3xl font-bold mt-2 text-blue-600">
                {avgOrdersPerCustomer}
              </p>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Customers Table */}
      <Card>
        <CardHeader>
          <CardTitle className="text-xl font-semibold">Customer List</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Contact</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredCustomers.length > 0 ? (
                filteredCustomers.map((customer) => (
                  <TableRow key={customer.id}>
                    <TableCell className="font-medium">
                      {customer.fullName || customer.name}
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-col">
                        <span className="text-sm">{customer.phone}</span>
                        <span className="text-xs text-muted-foreground">
                          {customer.email}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => openCustomerDetails(customer)}
                      >
                        View Details
                      </Button>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell
                    colSpan={3}
                    className="text-center py-4 text-muted-foreground"
                  >
                    No customers found matching your criteria
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* Customer Details Dialog */}
      {selectedCustomer && (
        <Dialog
          open={isCustomerDetailsOpen}
          onOpenChange={setIsCustomerDetailsOpen}
        >
          <DialogContent className="max-h-[90vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle>Customer Details</DialogTitle>
            </DialogHeader>
            <div className="py-4 space-y-5">
              <Card>
                <CardHeader>
                  <CardTitle className="text-lg font-semibold">
                    Profile
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    <div className="flex justify-between flex-wrap gap-4">
                      <div className="flex items-center gap-3">
                        <User className="h-5 w-5 text-muted-foreground" />
                        <div>
                          <p className="text-sm font-medium">Name</p>
                          <p className="text-sm text-muted-foreground">
                            {displayCustomer?.fullName || displayCustomer?.name}
                          </p>
                        </div>
                      </div>

                      <div className="flex items-center gap-3">
                        <Mail className="h-5 w-5 text-muted-foreground" />
                        <div>
                          <p className="text-sm font-medium">Email</p>
                          <p className="text-sm text-muted-foreground">
                            {selectedCustomer.email}
                          </p>
                        </div>
                      </div>
                    </div>
                    <div className="flex justify-between flex-wrap gap-4">
                      <div className="flex items-center gap-3">
                        <Phone className="h-5 w-5 text-muted-foreground" />
                        <div>
                          <p className="text-sm font-medium">Phone</p>
                          <p className="text-sm text-muted-foreground">
                            {selectedCustomer.phone}
                          </p>
                        </div>
                      </div>
                      <div className="flex items-center gap-3">
                        <ShoppingBag className="h-5 w-5 text-muted-foreground" />
                        <div>
                          <p className="text-sm font-medium">Total Orders</p>
                          <p className="text-sm text-muted-foreground">
                            {displayCustomer?.totalOrders ?? 0} orders
                          </p>
                        </div>
                      </div>
                    </div>
                  </div>
                </CardContent>
              </Card>

              {/* Order History */}
              <Card>
                <CardHeader>
                  <CardTitle className="text-lg font-semibold">
                    Order History
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Order ID</TableHead>
                        <TableHead>Amount</TableHead>
                        <TableHead>Items</TableHead>
                        <TableHead>Payment</TableHead>
                        <TableHead>Status</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {customerOrders?.length > 0 ? (
                        customerOrders.map((order) => (
                          <TableRow key={order.id}>
                            <TableCell className="font-medium">
                              #{order.id}
                            </TableCell>
                            <TableCell>
                              ₹{Number(order.totalAmount || 0).toLocaleString()}
                            </TableCell>
                            <TableCell>
                              {(order.items || []).map((orderItem, idx) => (
                                <p key={orderItem.id || idx} className="text-xs">
                                  {orderItem.product?.name?.slice(0, 20)}...
                                </p>
                              ))}
                            </TableCell>
                            <TableCell>{order.paymentType}</TableCell>
                            <TableCell>
                              <Badge
                                className={getStatusColor(order.status)}
                                variant="secondary"
                              >
                                {order.status || "COMPLETED"}
                              </Badge>
                            </TableCell>
                          </TableRow>
                        ))
                      ) : (
                        <TableRow>
                          <TableCell
                            colSpan={5}
                            className="text-center py-4 text-muted-foreground"
                          >
                            No orders found for this customer.
                          </TableCell>
                        </TableRow>
                      )}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            </div>
            <DialogFooter>
              <Button onClick={() => setIsCustomerDetailsOpen(false)}>
                Close
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
};

export default Customers;
