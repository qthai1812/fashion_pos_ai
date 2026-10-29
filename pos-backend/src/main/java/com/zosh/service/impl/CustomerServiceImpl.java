package com.zosh.service.impl;

import com.zosh.modal.Customer;
import com.zosh.modal.Order;
import com.zosh.repository.CustomerRepository;
import com.zosh.repository.OrderRepository;
import com.zosh.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Customer createCustomer(Customer customer) {
        if (customer.getCreatedAt() == null) {
            customer.setCreatedAt(LocalDateTime.now());
        }
        if (customer.getLoyaltyPoints() == null || customer.getLoyaltyPoints() < 0) {
            customer.setLoyaltyPoints(0);
        }
        customer.setUpdatedAt(LocalDateTime.now());
        return customerRepository.save(customer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Customer updateCustomer(Long id, Customer customer) throws Exception {
        Customer customerToUpdate = customerRepository.findByIdWithLock(id).orElseThrow(
                () -> new Exception("Customer not found")
        );
        if (customer.getFullName() != null && !customer.getFullName().trim().isEmpty()) {
            customerToUpdate.setFullName(customer.getFullName().trim());
        }
        if (customer.getEmail() != null) {
            customerToUpdate.setEmail(customer.getEmail().trim());
        }
        if (customer.getPhone() != null && !customer.getPhone().trim().isEmpty()) {
            customerToUpdate.setPhone(customer.getPhone().trim());
        }
        if (customer.getLoyaltyPoints() != null) {
            customerToUpdate.setLoyaltyPoints(Math.max(0, customer.getLoyaltyPoints()));
        }
        customerToUpdate.setUpdatedAt(LocalDateTime.now());
        return customerRepository.save(customerToUpdate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCustomer(Long id) throws Exception {
        Customer customerToDelete = customerRepository.findById(id).orElseThrow(
                () -> new Exception("Customer not found")
        );
        List<Order> customerOrders = orderRepository.findByCustomerId(id);
        for (Order order : customerOrders) {
            order.setCustomer(null);
            orderRepository.save(order);
        }
        customerRepository.delete(customerToDelete);
    }

    @Override
    @Transactional(readOnly = true)
    public Customer getCustomer(Long id) throws Exception {
        return customerRepository.findById(id).orElseThrow(
                () -> new Exception("Customer not found")
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() throws Exception {
        return customerRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> searchCustomers(String keyword) throws Exception {
        if (keyword == null || keyword.trim().isEmpty()) {
            return customerRepository.findAll();
        }
        return customerRepository.searchCustomers(keyword.trim());
    }
}
