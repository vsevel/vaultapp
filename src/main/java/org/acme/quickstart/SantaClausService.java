package org.acme.quickstart;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class SantaClausService {

    @Inject
    EntityManager em;

    @Transactional
    public void createGift(String giftDescription) {
        Gift gift = new Gift();
        gift.setName(giftDescription);
        em.persist(gift);
    }

    public List<String> findAll() {
        List<Gift> list = em.createQuery("select g from Gift g").getResultList();
        return list.stream().map(gift -> gift.getId() + ":" + gift.getName()).collect(Collectors.toList());
    }

}