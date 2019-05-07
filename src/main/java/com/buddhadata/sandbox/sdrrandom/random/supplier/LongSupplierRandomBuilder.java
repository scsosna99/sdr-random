/*
 * Copyright (c) 2019  Scott C. Sosna  ALL RIGHTS RESERVED
 *
 */

package com.buddhadata.sandbox.sdrrandom.random.supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.function.LongSupplier;

@Component
public class LongSupplierRandomBuilder {

    @Autowired
    private ApplicationContext appContext;

    /**
     * Defines the class used for generating Longs used when getting bits for the random numbmer generator.
     */
    @Value("${jukebox.random.supplier}")
    private String supplierClassName;

    public LongSupplierRandomBuilder() {
    }

    public Random create() {

        Random toReturn;
        if (supplierClassName != null) {

            try {
                //  Attempt to use reflection to get the constructor of the class and create an instance.
                Class supplier = Class.forName(supplierClassName);
                Object o = appContext.getBean(supplier);

                //  Now create the new Random number generator using the long supplier just instantiated.
                toReturn = new LongSupplierRandom ((LongSupplier) o);

                //  If the instance is a lifecycle, need to start it.
                if (o instanceof Lifecycle) {
                    ((Lifecycle) o).start();
                }
            } catch (Throwable t) {
                System.out.println ("Exception creating byte supplier: " + t);
                toReturn = new Random(System.currentTimeMillis());
            }

        } else {
            //  No class name supplied, so just use plain-old Java random-number generator.
            toReturn = new Random(System.currentTimeMillis());
        }


        return toReturn;
    }
}
