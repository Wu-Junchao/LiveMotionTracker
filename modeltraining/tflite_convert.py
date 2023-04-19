from tensorflow import lite
import tensorflow as tf
converter = lite.TFLiteConverter.from_saved_model('./models/Demo1_25_dual_imu/12')

converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.experimental_new_converter=True
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS,
tf.lite.OpsSet.SELECT_TF_OPS]

tfmodel = converter.convert()
open('demo1_25_dual.tflite', 'wb').write(tfmodel)