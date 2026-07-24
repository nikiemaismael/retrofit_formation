package com.bank.core.application.port.in;

/** Port d'entrée : exécution d'un virement. */
public interface TransferUseCase {

    /**
     * @return la référence de l'opération créée.
     */
    String execute(TransferCommand command);
}
