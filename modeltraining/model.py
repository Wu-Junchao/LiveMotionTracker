
from tensorflow.keras.layers import Dropout,LSTM, Multiply, Reshape,  Input,  Dense, BatchNormalization
from tensorflow.keras.models import Model
import tensorflow as tf


def build_model(imu_length=5):

        # IMU data
        imu_data = Input(shape=(imu_length, 6), name='imu_data')
        bn = BatchNormalization()(imu_data)
        imu_lstm_1 = LSTM(128, return_sequences=True, name='imu_lstm_1')(bn)  # 128, 256

        # # channel-wise IMU attention
        # reshape_imu = Reshape((1, imu_length * 128))(imu_lstm_1) 
        # imu_mask = Dense(128, activation='relu', use_bias=False, name='imu_mask_relu')(reshape_imu)
        # imu_mask = Dense(imu_length * 128, activation='sigmoid', use_bias=False, name='imu_mask_sigmoid')(imu_mask)
        # imu_att_fea = Multiply()([reshape_imu, imu_mask])


        # Selective features
        forward_lstm_1 = LSTM(512, dropout=0.25, return_sequences=False, name='forward_lstm_1')(
            imu_lstm_1)  # dropout_W=0.2, dropout_U=0.2
        reshaped_fea = Reshape((1,512))(forward_lstm_1) 
        forward_lstm_2 = LSTM(512, return_sequences=True, name='forward_lstm_2')(reshaped_fea)

        fc_class = (Dense(128, activation='relu'))(forward_lstm_2)  # tanh
        dropout_class_1 = (Dropout(0.25))(fc_class)
        fc_class_2 = (Dense(64, activation='relu'))(dropout_class_1)  # tanh
        reshape_class = Reshape((64,))(fc_class_2) 
        logit = (Dense(14,name = 'label'))(reshape_class)
        label = tf.keras.layers.Activation('softmax')(logit)
        
        model = Model(inputs=imu_data, outputs=label)
 
        return model
