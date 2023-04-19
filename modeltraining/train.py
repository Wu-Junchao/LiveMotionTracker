"""
Training deep mmwave+imu odometry from pseudo ground truth
"""
import os
import pickle
import sys
import tensorflow as tf
from os.path import join
import inspect
currentdir = os.path.dirname(os.path.abspath(inspect.getfile(inspect.currentframe())))
parentdir = os.path.dirname(currentdir)
os.sys.path.insert(0, parentdir)
import numpy as np
import yaml
import glob
import json

# utiliy
from model import build_model
from model_2_imu import build_model

def main():
    print('For 2022 data!')
    MODEL_NAME = 'Demo1_25_dual_imu_bn'
    IMU_LENGTH = 25
    print('IMU LENGTH is {}'.format(IMU_LENGTH))

    model_dir = join('./models', MODEL_NAME)

    model = build_model(imu_length=IMU_LENGTH)

    model.summary()


    # grap training files
    lr = 0.00001
    adam = tf.keras.optimizers.Adam(learning_rate= lr)
    
    model.compile(optimizer=adam, loss=tf.keras.losses.CategoricalCrossentropy())

    training_files = ['data/tf_2022_dual_imu_data_25']
    datasets = []
    for training_file in training_files:
            with open(training_file + '/element_spec' , 'rb') as in_:
                es = pickle.load(in_)

            loaded = tf.data.experimental.load(
                 training_file, es, compression='GZIP'
            )
            datasets.append(loaded)

    dataset = datasets[0]

    for i in range(len(datasets)-1):
        dataset = dataset.concatenate(datasets[1+i])
    dataset = dataset.shuffle(10)
    val_set = dataset.take(1000).batch(500)
    dataset = dataset.skip(1000)

    for e in range(30):
        print("|-----> epoch %d" % e)
        training_dataset = dataset.shuffle(120).batch(len(dataset))
        training_dataset
        print(len(dataset)," training samples")
        
        for elem in training_dataset:
            respeck,thingy,y = elem
            print(respeck.shape)
            print(thingy.shape)
            print(y.shape)
            model.fit({'imu_data_1': respeck, 'imu_data_2': thingy}, y, batch_size =128)
            # model.fit({'imu_data': respeck}, y, batch_size =128)
        if ((e % 1) == 0):
            for elem in val_set:
                respeck,thingy,y = elem = elem
                result = model({'imu_data_1': respeck, 'imu_data_2': thingy})
                p_c = tf.argmax(result,1) 
                target = tf.argmax(y,1)
                print(np.unique((p_c==target), return_counts=True))
            model.save(join(model_dir, str(e)))
           



    print('Finished training ')

if __name__ == "__main__":
    os.system("hostname")
    main()
