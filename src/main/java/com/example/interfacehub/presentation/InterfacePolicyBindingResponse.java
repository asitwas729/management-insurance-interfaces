package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.policy.InterfacePolicyBinding;

public record InterfacePolicyBindingResponse(
    Long id,
    String interfaceCode,
    String policyName,
    String partnerId,
    Integer priority,
    boolean enabled
) {
    public static InterfacePolicyBindingResponse from(InterfacePolicyBinding binding) {
        return new InterfacePolicyBindingResponse(
            binding.getId(),
            binding.getInterfaceDefinition().getInterfaceCode(),
            binding.getPolicyTemplate().getPolicyName(),
            binding.getPartnerId(),
            binding.getPriority(),
            binding.isEnabled()
        );
    }
}
